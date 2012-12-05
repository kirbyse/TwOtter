package backend;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

/**
 * Database Portal that acts as a portal to the program database. Methods are provided for generating HTML documents
 * for the newsfeed and profile for any given user.
 * @author Kyle Rogers
 * Requires SQLite-JDBC
 */
public class DBPortal {

	/**
	 * Open a file and store each line in a String
	 * @param filename location of the file
	 * @return Each line of the file stored in an ArrayList
	 */
	public static ArrayList<String> readFileByLine(String filename)
	{
		ArrayList<String> lines = new ArrayList<String>();
		try{
			// Open the file that is the first 
			// command line parameter
			FileInputStream fstream = new FileInputStream(filename);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			//Read File Line By Line
			while ((strLine = br.readLine()) != null) lines.add (strLine);
			//Close the input stream
			in.close();
		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}

		return lines;
	}
	
	private static void populate() throws SQLException, FileNotFoundException
	{
		ArrayList<String> firstNames = readFileByLine("tmp_txt/Given-Names");
		ArrayList<String> lastNames = readFileByLine("tmp_txt/Family-Names");
		ArrayList<String> passwords = readFileByLine("tmp_txt/common-passwords.txt");
		String book = readFile("tmp_txt/PlatosRepublic.txt");
		book += readFile("tmp_txt/BuddhistPsalms.txt");
		book += readFile("tmp_txt/KingJamesBible.txt");
		book += readFile("tmp_txt/Koran.txt");
		book += readFile("tmp_txt/ThusSpakeZazrthustra.txt");
		
		ArrayList<String> messages = new ArrayList<String>();
		String[] mArray = book.split("\\.");
		
		for (String m : mArray)
		{
			m = m.trim();
			if (m.length() >= 10) messages.add(m); 
		}
		
		DBPortal db = new DBPortal();
		Random rnd = new Random();
		ArrayList<User> users = new ArrayList<User>();
		for (int i = 0 ; i < 100 ; i++)
		{
			String lName = lastNames.get(rnd.nextInt(lastNames.size()));
			for (int j = 0 ; j <= rnd.nextInt(5) ; j++)
			{
				String fName = firstNames.get(rnd.nextInt(firstNames.size()));
				String password = passwords.get(rnd.nextInt(passwords.size()));
				users.add(new User(lName + fName.substring(0,2), lName + fName.substring(0,2) + "@gmail.com", "", "pic1.jpg", fName + " " + lName));
				db.createUser(lName + fName.substring(0,2), "", lName + fName.substring(0,2) + "@gmail.com", "pic" + rnd.nextInt(4) + ".jpg", password, fName + " " + lName);
				
			}
			System.out.println("Added users with " + lName + " for last name");
		}
		
		for (User u : users)
		{
			for (int i = 0 ; i < rnd.nextInt(10) ; i++)
			{
				db.follow(u.username, users.get(rnd.nextInt(users.size())).username);
			}
			for (int i = 0 ; i < rnd.nextInt(10) ; i++)
			{
				String mess = messages.get(rnd.nextInt(messages.size()));
				if (mess.length() >= 140) mess = mess.substring(0,140);
				db.createPostWithUsername(messages.get(rnd.nextInt(messages.size())), u.username);
			}
			System.out.println("Posts for " + u.username + " finished");
		}
		
		
	}
	
	/**
	 * Used for testing DBPortal.java
	 * @param args
	 * @throws SQLException
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws SQLException, FileNotFoundException
	{
		DBPortal portal = new DBPortal();
		populate();
	}

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
			return prepStmt.execute();
		} catch(SQLException e)
		{
			return false;
		}
	}
	
	public boolean follow(String follower, String following) throws SQLException
	{
		PreparedStatement prepStmt = conn.prepareStatement(FOLLOW_STATEMENT);
		prepStmt.setString(1, follower);
		prepStmt.setString(2, following);
		try{
			return prepStmt.execute();
		}catch(SQLException e) {return false;}
	}

	/**
	 * 
	 * @param message
	 * @param sessionID
	 * @return
	 */
	public boolean createPost(String message, String sessionID)
	{
		String username = "";
		try {
			username = getUsernameByID(sessionID);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return createPost(message,username);
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
			Random rnd = new Random();
			prepStmt = conn.prepareStatement(CREATE_POST_STATEMENT);
			prepStmt.setString(1, message);
			prepStmt.setString(2, username);
			prepStmt.execute();
			prepStmt = conn.prepareStatement(CREATE_POSTED_STATEMENT);
			prepStmt.setString(1, username);
			String format = "yyyy-MM-dd hh:mm:ss.SS a";
			SimpleDateFormat sdf = new SimpleDateFormat(format);
			Date now = new Date(1351728000 + rnd.nextInt(1354320000-1351728000));
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
		return rs.next();
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
		return rs.next();
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
		return rs.next();
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
		rs.next();
		return rs.getString(1);
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
		return rs.getString("username");
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
		return getHTML(username, username,true);
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
		for (Post p : posts) postHTML += p.toHTML();
		String tempF = DBPortal.readFile("src" + DBPortal.SEP + "backend" + DBPortal.SEP + "HTMLTemplates" + DBPortal.SEP + "template.html");
		String page = tempF.replaceAll("%user%", requestingUser);
		page = page.replaceAll("%username%",username);
		page = page.replaceFirst("%userInformation%", userHTML);
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
		FileInputStream stream = new FileInputStream(new File(pathname));
		try {
			FileChannel fc = stream.getChannel();
			MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
			/* Instead of using default, pass in a decoder. */
			return Charset.defaultCharset().decode(bb).toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
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