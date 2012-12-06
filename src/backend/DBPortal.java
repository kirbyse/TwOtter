package backend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Database Portal that acts as a portal to the program database. Methods are provided for generating HTML documents
 * for the newsfeed and profile for any given user.
 * @author Kyle Rogers
 * Requires SQLite-JDBC
 */
public class DBPortal {

	public static final char SEP = File.separatorChar;

	private Connection conn;

	// SQL statement for retrieving all of the posts that a user has posted, including reposts
	private static final String GET_USER_POSTS_STATEMENT = 
			"SELECT DISTINCT(POST.postID),POSTED.username,POST.username,POSTED.timestamp,POST.message,USER.picture " + 
					"FROM USER JOIN POSTED ON USER.username=POST.username JOIN POST ON POST.postid=POSTED.postid WHERE " + 
					"POSTED.username= ? ORDER BY timestamp DESC";

	// SQL statement for retrieving all of the posts for the users that a given user follows
	// This statement returns both an original post and each repost. It should only return the oldest post
	private static final String GET_FEED_STATEMENT = 
			"SELECT DISTINCT(POST.postID),POSTED.username,POST.username,POSTED.timestamp,POST.message,USER.picture " + 
					"FROM POST JOIN POSTED ON POST.postid=POSTED.postid JOIN FOLLOWING ON FOLLOWING.followee=POSTED.username " + 
					"JOIN USER ON POST.username=USER.username WHERE FOLLOWING.follower=? ORDER BY timestamp DESC";

	// SQL statement for retrieving getting all of the information for a given user
	private static final String GET_USER_INFO_STATEMENT = 
			"SELECT username,email,description,picture,name FROM USER WHERE username=?";

	private static final String CREATE_POST_STATEMENT = 
			"INSERT INTO POST VALUES(null,?,?)";
	private static final String CREATE_POSTED_STATEMENT = 
			"INSERT INTO POSTED VALUES( ?,(select last_insert_rowid()),null,?)";
	private static final String FOLLOW_STATEMENT = "INSERT INTO FOLLOWING VALUES(?,?)";


	/**
	 * Creates a portal to src/backend/twotter.db
	 */
	public DBPortal()
	{
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:src" + SEP + "backend" + SEP + "twotter.db");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e)	{
			e.printStackTrace();
		}
	}
	
	public boolean isFollowing(String follower, String following) throws SQLException
	{
		PreparedStatement prepStmt = conn.prepareStatement("SELECT * FROM FOLLOWING WHERE FOLLOWER = ? AND FOLLOWEE = ?");
		prepStmt.setString(1,follower);
		prepStmt.setString(2, following);
		ResultSet rs = prepStmt.executeQuery();
		boolean b = rs.next();
		rs.close();
		return b;
	}
	
	public boolean createUser(String username, String description, String email, String picture, String passHash, String name) throws SQLException
	{
		String cmd = "INSERT INTO USER VALUES(?,?,?,?,?,?,?)";
		PreparedStatement prepStmt = conn.prepareStatement(cmd);
		prepStmt.setString(1, username);
		prepStmt.setString(2, randomString(20));
		prepStmt.setString(3, passHash);
		prepStmt.setString(4, email);
		prepStmt.setString(5, description);
		prepStmt.setString(6, picture);
		prepStmt.setString(7, name);
		try{
			prepStmt.execute();
			follow(username,username);
			return true;
		} catch(SQLException e)
		{
			return false;
		}
	}
	
	public boolean resqueak(String sessionID, int postID)
	{
		try {
			PreparedStatement prepStmt = conn.prepareStatement("INSERT INTO POSTED VALUES(?,?,1,?)");
			prepStmt.setString(1,this.getUsernameByID(sessionID));
			prepStmt.setLong(2, postID);
			String format = "yyyy-MM-dd hh:mm:ss.SS a";
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			Date now = new Date();
			prepStmt.setString(3, sdf.format(now));			
			return prepStmt.execute();
		} catch (SQLException e) {
			return false;
		}
	}
	
	public boolean deletePost(int postID)
	{
		try {
			PreparedStatement prepStmt = conn.prepareStatement("DELETE FROM POSTED WHERE postID = ?");
			prepStmt.setLong(1, postID);
			prepStmt.execute();
			PreparedStatement prepStmt2 = conn.prepareStatement("DELETE FROM POST WHERE postID = ?");
			prepStmt2.setLong(1, postID);
			return prepStmt2.execute();
		}
		catch(SQLException e)
		{
			return false;
		}
	}
	
	public boolean followByUsername(String follower, String following) throws SQLException
	{
		PreparedStatement prepStmt = conn.prepareStatement(FOLLOW_STATEMENT);
		prepStmt.setString(1, follower);
		prepStmt.setString(2, following);
		try{
			return prepStmt.execute();
		}catch(SQLException e) {return false;}
	}
	
	public boolean follow(String sessionID, String following)
	{
		try {
			followByUsername(this.getUsernameByID(sessionID),following);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}
	
	public boolean unfollow(String sessionID, String following)
	{
		try {
			PreparedStatement prepStmt = conn.prepareStatement("DELETE FROM FOLLOWING WHERE FOLLOWER = ? AND FOLLOWEE = ?");
			prepStmt.setString(1, this.getUsernameByID(sessionID));
			prepStmt.setString(2, following);
			prepStmt.execute();
			return true;
		}catch(Exception e) {return false;}
	}

	public String search(String sessionID, String term)
	{
		try{
			PreparedStatement prepStmt = conn.prepareStatement("SELECT username,email,description,picture,name FROM USER WHERE username LIKE ? OR name LIKE ?");
			prepStmt.setString(1, "%" + term + "%");
			prepStmt.setString(2, "%" + term + "%");
			ResultSet rs = prepStmt.executeQuery();
			ArrayList<User> users = new ArrayList<User>();
			String userHTML = "";
			while (rs.next())
			{
				User u = new User(rs);
				users.add(u);
				userHTML += u.toSearchHTML();
			}
			String html = readFile("src" + SEP + "backend" + SEP + "HTMLTemplates" + SEP + "search.html");
			
			html = html.replaceAll("%user%", this.getUsernameByID(sessionID));
			html = html.replaceAll("%userHTML%", userHTML);
			html = html.replaceAll("%query%", term);
			return html;
		}catch(Exception e)
		{
			e.printStackTrace();
		} return "";
	}

	/**
	 * 
	 * @param message
	 * @param username
	 * @return
	 */
	public boolean createPostWithUsername(String message, String username)
	{
		PreparedStatement prepStmt;
		try {
			prepStmt = conn.prepareStatement(CREATE_POST_STATEMENT);
			prepStmt.setString(1, message);
			prepStmt.setString(2, username);
			prepStmt.execute();
			prepStmt = conn.prepareStatement(CREATE_POSTED_STATEMENT);
			prepStmt.setString(1, username);
			String format = "yyyy-MM-dd hh:mm:ss.SS a";
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			Date now = new Date();
			prepStmt.setString(2, sdf.format(now));
			return prepStmt.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Checks to see if the given user exists
	 * @param username User that is being checked for existence
	 * @return Whether the user exists or not
	 * @throws SQLException
	 */
	public boolean userExists(String username) throws SQLException
	{
		String query = "SELECT username FROM USER WHERE USERNAME = ?";
		PreparedStatement prepStmt = conn.prepareStatement(query);
		prepStmt.setString(1, username);
		ResultSet rs = prepStmt.executeQuery();
		boolean ans = rs.next();
		rs.close();
		return ans;
	}
	
	
	/**
	 * Check to see if an email is already taken
	 * @param email
	 * @return True if the email already exists, false if it is not present in the database
	 * @throws SQLException
	 */
	public boolean emailExists(String email) throws SQLException
	{
		String query = "SELECT username FROM USER WHERE email = ?";
		PreparedStatement prepStmt = conn.prepareStatement(query);
		prepStmt.setString(1,email);
		ResultSet rs = prepStmt.executeQuery();
		boolean ans = rs.next();
		rs.close();
		return ans;
	}
	
	/**
	 * Check if login information is valid
	 * @param username
	 * @param password
	 * @return True if the login information is valid, otherwise false
	 * @throws SQLException
	 */
	public boolean checkLogin(String username, String password) throws SQLException
	{
		String query = "SELECT username FROM USER WHERE USERNAME = ? AND PASSWORD = ?";
		PreparedStatement prepStmt = conn.prepareStatement(query);
		prepStmt.setString(1, username);
		prepStmt.setString(2, password);
		ResultSet rs = prepStmt.executeQuery();
		boolean ans = rs.next();
		rs.close();
		return ans;
	}
	
	/**
	 * Find the session ID for a user
	 * @param username
	 * @return
	 * @throws SQLException
	 */
	public String retrieveSessionID(String username) throws SQLException
	{
		String query = "SELECT sessionID FROM USER WHERE USERNAME = ?";
		PreparedStatement prepStmt = conn.prepareStatement(query);
		prepStmt.setString(1, username);
		ResultSet rs = prepStmt.executeQuery();
		if (!rs.next()) return null;;
		String ret = rs.getString(1);
		rs.close();
		return ret;
	}

	/**
	 * Find username by session ID
	 * @param sessionID
	 * @return
	 * @throws SQLException0
	 */
	public String getUsernameByID(String sessionID) throws SQLException
	{
		PreparedStatement prepStmt = conn.prepareStatement("SELECT username FROM USER WHERE sessionID = ?");
		prepStmt.setString(1, sessionID);
		ResultSet rs = prepStmt.executeQuery();
		if (!rs.next()) return null;;
		String username = rs.getString("username");
		rs.close();
		return username;
	}

	/**
	 * Dynamically generates an HTML page for username's news feed
	 * @param username The user requesting a news feed
	 * @return HTML page for the news feed
	 * @throws FileNotFoundException one of the template HTML files is missing
	 * @throws SQLException There was an error in twotter.db or with the username
	 */
	public String getNewsFeedHTML(String username) throws FileNotFoundException, SQLException
	{
		return getHTML(null, username,true);
	}

	/**
	 * Dynamically generates an HTML page for username's profile
	 * @param sessionID The user requesting the profile
	 * @param username The user who's profile is being requested
	 * @return HTML page for the profile
	 * @throws FileNotFoundException one of the template HTML files is missing
	 * @throws SQLException There was an error in twotter.db or with the username
	 */	
	public String getProfileHTML(String sessionID, String username) throws FileNotFoundException, SQLException
	{
		return getHTML(sessionID, username,false);
	}
	
	public boolean setName(String sessionID, String name)
	{
		try{
			PreparedStatement prepStmt = conn.prepareStatement("UPDATE USER SET name = ? WHERE sessionID = ?");
			prepStmt.setString(1, name);
			prepStmt.setString(2, sessionID);
			prepStmt.executeUpdate();
			return true;
		}catch(Exception e) {return false;}
	}
	
	public boolean setDescription(String sessionID, String description)
	{
		try{
			PreparedStatement prepStmt = conn.prepareStatement("UPDATE USER SET description = ? WHERE sessionID = ?");
			prepStmt.setString(1, description);
			prepStmt.setString(2, sessionID);
			prepStmt.executeUpdate();
			return true;
		}catch(Exception e) {return false;}
	}
	
	public boolean setPicture(String sessionID, String picture)
	{
		try{
			PreparedStatement prepStmt = conn.prepareStatement("UPDATE USER SET picture = ? WHERE sessionID = ?");
			prepStmt.setString(1, picture);
			prepStmt.setString(2, sessionID);
			prepStmt.executeUpdate();
			return true;
		}catch(Exception e) {return false;}
	}

	/**
	 * Dynamically generates an HTML page for either a newsfeed or a profile
	 * @param username The user who's information is to be retrieved
	 * @param newsfeed If true - retrieves a newsfeed for username, else - retrieves the profile for username
	 * @return String of HTML. If the requested user does not exist, returns null
	 * @throws FileNotFoundException One of the HTML template files is missing
	 * @throws SQLException twotter.db has an error or an error in SQL inputs
	 */
	private String getHTML(String sessionID, String username, boolean newsfeed) throws FileNotFoundException, SQLException
	{
		String requestingUser = username;
		if (sessionID != null)
			requestingUser = getUsernameByID(sessionID);
		User u = getUser(username);
		if (u == null) return null;
		String userHTML = getUser(username).toHTML();
		String postHTML = "";
		ArrayList<Post> posts = getPosts(username,newsfeed);
		if (posts.size() == 0) postHTML = readFile("src/backend/HTMLTemplates/nothing_here.html");
		for (Post p : posts)
		{
			if (p.getPostBy().equals(requestingUser))
			{
				postHTML += p.toHTML().replace("<!-- Delete -->", "Delete");
			}
			else
			{
				postHTML += p.toHTML().replace("<!-- ReSqueak -->", "ReSqueak");
			}
		}
		String tempF = DBPortal.readFile("src" + DBPortal.SEP + "backend" + DBPortal.SEP + "HTMLTemplates" + DBPortal.SEP + "template.html");
		String page = "";
		page = tempF.replaceAll("%user%", requestingUser);
		page = page.replaceFirst("%userInformation%", userHTML);
		if (username.equals(requestingUser)) page = 
			page.replaceAll("%buttonArea%", readFile("src" + SEP + "backend" + SEP + "HTMLTemplates" + SEP+ "postBox" ));
		else if (!isFollowing(requestingUser,username)) page = 
			page.replaceAll("%buttonArea%", readFile("src"+SEP+"backend"+SEP+ "HTMLTemplates" + SEP+ "followButton"));
		else page = 
			page.replaceAll("%buttonArea%", readFile("src"+SEP+"backend"+SEP+ "HTMLTemplates" + SEP+ "unfollowButton"));
		page = page.replaceAll("%username%",username);
		page = page.replaceFirst("%posts%", postHTML);
		return page;
	}


	/**
	 * Retrieve the posts for a either the user's news feed or the posts that the user has posted
	 * @param username
	 * @param newsfeed True returns 
	 * @return
	 * @throws SQLException
	 */
	private ArrayList<Post> getPosts(String username, boolean newsfeed) throws SQLException
	{
		ArrayList<Post> posts = new ArrayList<Post>();
		PreparedStatement prepStmt = conn.prepareStatement(newsfeed ? GET_FEED_STATEMENT : GET_USER_POSTS_STATEMENT);
		prepStmt.setString(1, username);
		ResultSet rs = prepStmt.executeQuery();
		while (rs.next()) posts.add(new Post(rs));
		rs.close();
		return posts;
	}

	/**
	 * 
	 * @param sessionID
	 * @param follower
	 * @return
	 * @throws SQLException
	 * @throws FileNotFoundException
	 */
	public String followingPage(String username, boolean follower) throws SQLException, FileNotFoundException
	{
		String query = (follower) ? 
				"SELECT username,email,description,picture,name FROM USER WHERE USERNAME IN (SELECT FOLLOWEE FROM FOLLOWING WHERE FOLLOWER = ?)" 
				: 
				"SELECT username,email,description,picture,name FROM USER WHERE USERNAME IN (SELECT FOLLOWER FROM FOLLOWING WHERE FOLLOWEE = ?)";
		PreparedStatement prepStmt = conn.prepareStatement(query);
		prepStmt.setString(1,username);
		ResultSet rs = prepStmt.executeQuery();
		String userHTML = "";
		while (rs.next())
			userHTML += new User(rs).toSearchHTML();
		
		String html = readFile("src" + SEP + "backend" + SEP + "HTMLTemplates" + SEP +((follower)? "following.html":"followers.html"));
		
		html = html.replaceAll("%user%", username);
		html = html.replaceAll("%userHTML%", userHTML);
		return html;
	}
	
	private User getUser(String username) throws SQLException
	{
		User u = null;
		PreparedStatement prepStmt = conn.prepareStatement(GET_USER_INFO_STATEMENT);
		prepStmt.setString(1, username);
		ResultSet rs = prepStmt.executeQuery();
		rs.next();
		try{
			u = new User(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5));
		}catch(Exception e)	{
			return null;
		}
		finally{
			rs.close();
		}
		return u;
	}


	/**
	 * Opens a file and outputs a string
	 * @param pathname Location of the file to be opened
	 * @return A string of the text in the file
	 * @throws FileNotFoundException The file does not exist
	 */
	public static String readFile(String pathname) throws FileNotFoundException
	{
		String str = "";
		FileInputStream stream = new FileInputStream(new File(pathname));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			/* Instead of using default, pass in a decoder. */
			str = Charset.defaultCharset().decode(bb).toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				stream.close();
				return str;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return str;
	}
	
	public static String randomString(int size)
	{
		char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
		StringBuilder sb = new StringBuilder();
		SecureRandom random = new SecureRandom();
		for (int i = 0; i < size; i++) {
		    char c = chars[random.nextInt(chars.length)];
		    sb.append(c);
		}
		return sb.toString();
	}

}