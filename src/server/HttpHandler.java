
package server;


import java.io.*;
import java.net.*;
import java.sql.SQLException;

import backend.DBPortal;

/**
 * HttpHandler servers as a handler for HttpServer which takes in the URL from the server, parses
 * the URL and then decides how the server should respond to the client. This handler works according
 * to HTTP and thus can be accessed with any HTTP web browser.
 * @author Eric
 * Requires DBPortal, HttpServer
 */
public class HttpHandler extends java.lang.Thread {
	DBPortal portal = new DBPortal();
	Socket client;
	OutputStream os=null;
	String sessionId;
	String DEFAULT_ID = "00000000000000000000";

	/**
	 * Creates an HttpHandler object with a Socket connecting the server to the client.
	 * Instantiates the sessionId to 00000000000000000000 by default.
	 * @param client The Socket that connects the server to the client
	 */
	public HttpHandler(Socket client) {
		this.client=client;
		sessionId = DEFAULT_ID;
	}

	/**
	 * Sends a 404 File Not Found message to the client when the file they are searching for is
	 * not found in the server.
	 * @throws IOException
	 */
	public void send404() throws IOException {
		handleFile("/404.html");
	}

	/**
	 * Sends a 500 Internal Server Error message to the client whenever there is an internal server
	 * error.
	 * @throws IOException
	 */
	public void send500() throws IOException {
		sendResponse(500,"500 - Invalid Server Request","text/html","<html><body>Error - invalid Request</body></html>".getBytes());
	}


	/**
	 * Sends and HTTP response from the server to the client.
	 * @param code The status code send to the client
	 * @param status The string of the status corresponding to the code
	 * @param type The content-type of the HTTP response message
	 * @param body The body of the HTTP response message
	 * @throws IOException
	 */
	public void sendResponse(int code, String status, String type, byte body[]) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " " + status + "\r\n");
		sb.append("Content-Length: " + body.length + "\r\n");
		sb.append("Content-Type: " + type + "\r\n");
		sb.append("\r\n");	//dont forget blank line
		os.write(sb.toString().getBytes());
		os.write(body);
		os.flush();
	}

	/**
	 * Sends an HTTP response message from the server to the client with a Set-Cookie header field
	 * to instantiate the sessionId cookie in the client's browser to maintain session.
	 * @param code The status code to be sent to the client
	 * @param status The status code message
	 * @param type The content-Type of the message
	 * @param body The body of the HTTP response
	 * @throws IOException
	 */
	public void sendCookieResponse(int code, String status, String type, byte body[]) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " " + status + "\r\n");
		sb.append("Content-Length: " + body.length + "\r\n");
		sb.append("Content-Type: " + type + "\r\n");
		sb.append("Set-Cookie: session=" + sessionId + "\r\n"); //Setting the sessionId for the client
		sb.append("\r\n");	//dont forget blank line
		os.write(sb.toString().getBytes());
		os.write(body);
		os.flush();
	}

	/**
	 * Sends only the HTTP response header from the server to the client, useful for when the 
	 * body is dynamically generated
	 * @param code The status code of the HTTP message
	 * @param status The status message of the HTTP message
	 * @param type The content-type of the HTTP message
	 * @throws IOException
	 */
	public void sendResponseHeader(int code, String status, String type) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " " + status + "\r\n");
		sb.append("Connection: close\r\n");
		sb.append("Content-Type: " + type + "\r\n");
		sb.append("\r\n");	//dont' forget blank line
		os.write(sb.toString().getBytes());
	}
	
	/**
	 * Sends only the HTTP response header from the server to the client, useful for when the 
	 * body is dynamically generated. This header contains a Set-Cookie field to set the sessionId
	 * cookie in the client's browser to maintain session.
	 * @param code The status code of the HTTP message
	 * @param status The status message of the HTTP message
	 * @param type The content-type of the HTTP message
	 * @throws IOException
	 */
	public void sendCookieResponseHeader(int code, String status, String type) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " " + status + "\r\n");
		sb.append("Connection: close\r\n");
		sb.append("Content-Type: " + type + "\r\n");
		sb.append("Set-Cookie: session=" + sessionId + "\r\n"); //Setting the sessionId for the client
		sb.append("\r\n");	//dont' forget blank line
		os.write(sb.toString().getBytes());
	}

	/**
	 * Gets the content-type of a url based on the extension at the end of the url
	 * @param URL The url to be parsed 
	 * @return The content-type of the url as entered in the content-type header
	 */
	public String getContentType(String URL) {
		//get extension
		String ext[] = URL.split("\\.");
		//System.err.println(ext.length);
		String e = ext[ext.length-1].toLowerCase();
		if ("txt".equals(e)) 
			return "text/txt";
		else if("html".equals(e) || "htm".equals(e))
			return "text/html";
		else if ("jpg".equals(e))
			return "image/jpg";
		else if ("gif".equals(e))
			return "image/gif";
		else if ("png".equals(e))
			return "image/png";
		else if("css".equals(e))
			return "text/css";
		else if("js".equals(e))
			return "text/javascript";
		else
			return "text/txt";
	}

	/**
	 * Security checks the url to ensure that clients can't go up a directory in the server
	 * @param url The url to be checked
	 * @return Returns the url if it is safe, null if not
	 */
	public String testURL(String url) {
		if (url.contains("..")) // do not allow path to move above
			return null;
		else
			return url;
	}

	/**
	 * Gets the path from the HTTP request message sent from the client
	 * @return The path from the HTTP request message sent from the client
	 * @throws IOException
	 */
	public String getRequest() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
		String get = br.readLine();
		if (get==null)
			throw new IOException("null string");
		String line = "empty";
		while (!line.equals("")) {
			line = br.readLine();
			line = line.toLowerCase();
			if(line.contains("session=")) { //If the HTTP request contains a cookie for sessionId
				sessionId = line.substring(line.indexOf("session=")+8);
			}
		}
		String parts[] =  get.split(" ");
		if (parts.length != 3 || !parts[0].toLowerCase().equals("get") || !parts[2].toLowerCase().startsWith("http/")) {
			send500();
			return null;
		}
		return parts[1];
	}

	/**
	 * Main entry point for HttpHandler object, must be called whenever a connection is established
	 */
	public void run() {
		try {
			os = client.getOutputStream();
			String URL = getRequest();
			handleURL(URL);

			os.close();
		} catch (Exception err) {
			err.printStackTrace();
		}
	}

	/**
	 * Parses the URL received from the client and decides how to handle the user's request
	 * @param URL The URL to be parsed
	 * @throws IOException
	 * @throws SQLException
	 */
	public void handleURL(String URL) throws IOException, SQLException {
		if (URL!= null) {
			if(sessionId.equals(DEFAULT_ID)) {
				if (URL.equals("/makeaprofile")) {
					handleFile("/makeAProfile.html");
				}
				else if (URL.startsWith("/makeaprofile") && URL.contains("username=") && URL.contains("password=")
						&& URL.contains("email=") && URL.contains("name=") && URL.contains("description=") 
						&& URL.contains("image=")) {
					//User made profile
					String[] profileParts = URL.split("=");
					String username = profileParts[1].substring(0, profileParts[1].length() - 9);
					String password = profileParts[2].substring(0, profileParts[2].length() - 6);
					String email = profileParts[3].substring(0, profileParts[3].length() - 7);
					String name = profileParts[4].replace('+', ' ').substring(0,profileParts[4].length() - 12);
					String description = profileParts[5].replace('+', ' ').substring(0, profileParts[5].length() - 6);
					String image = profileParts[6];
					username = replaceHex(username);
					password = replaceHex(password);
					email = replaceHex(email);
					name = replaceHex(name);
					description = replaceHex(description);
					image = "/" + replaceHex(image);
					portal.createUser(username, description, email, image, password, name);
					sessionId = portal.retrieveSessionID(username);
					sendLoginNewsFeed(username);
				}
				else if(URL.contains(".")) {
					handleFile(URL);
				}
				else if(URL.contains("username=") && URL.contains("password=")){
					getLogin(URL);
				}
				else {
					//By default send user to log in page if they have not logged
					handleFile("/Login.html");
				}
			}
			else { //SessionId maps to a user.
				if (URL.equals("/twotter") || URL.equals("/") || URL.equals("/home")) {
					sendNewsFeed();
				}
				else if(URL.equals("/Logout")) {
					sessionId = DEFAULT_ID;
					handleCookieFile("/Login.html");
				}
				else if(URL.contains("/followers.html")) {
					String[] userParts = URL.split("username=");
					String username = userParts[1];
					String page = portal.followingPage(username, true);
					sendResponse(200,"OK","text/html",page.getBytes());
				}
				else if(URL.contains("/following.html")) {
					String[] userParts = URL.split("username=");
					String username = userParts[1];
					String page = portal.followingPage(username, false);
					sendResponse(200,"OK","text/html",page.getBytes());
				}
				else if (URL.contains("username=") && URL.contains("password=") && !URL.contains("image=")) {
					//User just submitted log in information
					getLogin(URL);
				}
				else if (URL.contains("delete=")) {
					//User just submitted log in information
					String[] deleteParts = URL.split("delete="); //should only return an integer
					try {
						int deleteId = Integer.parseInt(deleteParts[1]);
						portal.deletePost(deleteId); //Delete the post selected
						String username = portal.getUsernameByID(sessionId);
						if(URL.startsWith("/" + username)) {
							userProfile(username);
						} else if(URL.startsWith("/home") || URL.startsWith("/Logout") || URL.startsWith("/makeAProfile") || URL.startsWith("/EditProfile")) {
							sendNewsFeed();
						} else {
							handleURL(deleteParts[0].substring(0,deleteParts[0].length() - 1)); //Just refresh page
						}
					} catch (NumberFormatException err) {
						sendNewsFeed();
					}
				}
				else if (URL.contains("post=")) {
					String[] parts = URL.split("post=");
					String post = parts[1];
					post = post.replace("+", " ");
					post = replaceHex(post);
					String username = portal.getUsernameByID(sessionId);
					portal.createPostWithUsername(post, username);
					sendNewsFeed();
				}
				else if(URL.contains("resqueak=")) {
					String[] resqueakParts = URL.split("resqueak="); //should only return an integer
					try {
						int resqueakId = Integer.parseInt(resqueakParts[1]);
						portal.resqueak(sessionId, resqueakId); //Delete the post selected
						String username = portal.getUsernameByID(sessionId);
						if(URL.startsWith("/" + username)) {
							userProfile(username);
						} else if(URL.startsWith("/home") || URL.startsWith("/Logout") || URL.startsWith("/makeAProfile") || URL.startsWith("/EditProfile")) {
							sendNewsFeed();
						} else {
							handleURL(resqueakParts[0].substring(0,resqueakParts[0].length() - 1)); //Just refresh page
						}
					} catch (NumberFormatException err) {
						sendNewsFeed();
					}
				}
				else if(URL.contains("unfollow=")) {
					String[] userParts = URL.split("unfollow=");
					String username = userParts[1];
					portal.unfollow(sessionId, username);
					sendNewsFeed();
				}
				else if (URL.contains("follow=")) {
					String[] userParts = URL.split("follow=");
					String username = userParts[1];
					portal.follow(sessionId, username);
					sendNewsFeed();
				}
				else if(URL.contains("search=")) {
					String[] searchParts = URL.split("search=");
					String search = searchParts[1];
					String page = portal.search(sessionId,search);
					sendResponse(200,"OK","text/html",page.getBytes());
				}
				else if(URL.startsWith("/EditProfile") && URL.contains("name=") && URL.contains("description=") && URL.contains("image=")) {
					getEditProfile(URL); //User Edited Profile
				}
				else if(portal.userExists(URL.substring(1))) {
					userProfile(URL.substring(1));
				}
				else if(URL.endsWith(".css") || URL.endsWith(".js") || URL.endsWith(".jpg") || URL.endsWith(".html") || URL.endsWith(".png")) {
					handleFile(URL);
				}
				else {
					send404();
				}
			}
		}
	}

	/**
	 * Handles reading a file and sending it to the client from the server
	 * @param URL The URL to be parsed for a file name
	 * @throws IOException
	 */
	public void handleFile(String URL) throws IOException {
		FileInputStream fin = null;
		try {
			fin = new FileInputStream("src/backend/HTMLTemplates" + URL);
		} catch (IOException err) {
			send404();
			return;
		}
		sendResponseHeader(200,"OK",getContentType(URL));
		byte buff[] = new byte[1000];
		int readLen;
		while((readLen=fin.read(buff)) != -1)
			os.write(buff,0,readLen);
		os.flush();
		fin.close();
	}
	
	/**
	 * Handles reading a file and sending it to the client from the server with a set-cookie header
	 * @param URL The URL to be parsed for a file name
	 * @throws IOException
	 */
	public void handleCookieFile(String URL) throws IOException {
		FileInputStream fin = null;
		try {
			fin = new FileInputStream("src/backend/HTMLTemplates" + URL);
		} catch (IOException err) {
			send404();
			return;
		}
		sendCookieResponseHeader(200,"OK",getContentType(URL));
		byte buff[] = new byte[1000];
		int readLen;
		while((readLen=fin.read(buff)) != -1)
			os.write(buff,0,readLen);
		os.flush();
		fin.close();
	}

	/**
	 * Generates the user profile based on the username and sends it to the client
	 * @param username The user whose profile you want to generate
	 * @throws IOException
	 * @throws SQLException
	 */
	public void userProfile(String username) throws IOException, SQLException { //Get someone else's Profile
		try {
			String body = portal.getProfileHTML(sessionId, username);
			sendResponse(200,"OK","text/html",body.getBytes());
		} catch (FileNotFoundException err) {
			send404();
		}
	}

	/**
	 * Sends the current logged in users profile to the client
	 * @throws SQLException
	 * @throws IOException
	 */
	public void sendProfile() throws SQLException, IOException {
		String username = portal.getUsernameByID(sessionId);
		String body = portal.getProfileHTML(sessionId, username);
		sendResponse(200,"OK","text/html",body.getBytes());
	}

	/**
	 * Handles the user logging in and then sends the user's profile if they logged in.
	 * If the user information for logging in is invalid sends a login error page.
	 * @param URL The URL to be parsed for user login information.
	 * @throws IOException
	 * @throws SQLException
	 */
	public void getLogin(String URL) throws IOException, SQLException {
		String responses[] = URL.split("=");
		String username = responses[1].substring(0, responses[1].length()-9);
		String password = responses[2];
		username = replaceHex(username);
		password = replaceHex(password);
		if(!username.equals("") && !password.equals("")) {
			if(portal.checkLogin(username, password)) { //Log in was successful
				sessionId = portal.retrieveSessionID(username);
				sendLoginNewsFeed(username);
			} else { //Login was unsuccessful
				handleFile("/LoginError.html");
			}
		}
		else {
			handleFile("/LoginError.html");
		}

	}

	/**
	 * Newsfeed that is sent when the user first logs on, sets the sessionId cookie for the client browser
	 * establishing session control until the user logs out.
	 * @param username The user whose news feed is sent
	 * @throws IOException
	 * @throws SQLException
	 */
	public void sendLoginNewsFeed(String username) throws IOException, SQLException {
		String body = portal.getNewsFeedHTML(username);
		sendCookieResponse(200,"OK","text/html",body.getBytes());
	}

	/**
	 * Sends the newsfeed of the currently logged in user without a set-cookie header
	 * @throws IOException
	 * @throws SQLException
	 */
	public void sendNewsFeed() throws IOException, SQLException {
		String username = portal.getUsernameByID(sessionId);
		String body = portal.getNewsFeedHTML(username);
		sendResponse(200,"OK","text/html",body.getBytes());
	}

	/**
	 * Handles editing a user's profile from the edit profile page.
	 * @param URL The url to be parsed for new user information.
	 * @throws IOException
	 * @throws SQLException
	 */
	public void getEditProfile(String URL) throws IOException, SQLException { //Justin will change this text
		String responses[] = URL.split("=");
		String name = responses[1].substring(0, responses[1].length()-12);
		String description = responses[2].substring(0,responses[2].length()-6);
		String image = responses[3];
		name = replaceHex(name);
		name = name.replace('+', ' ');
		image = "/" + replaceHex(image);
		description = replaceHex(description);
		description = description.replace('+', ' ');
		if(!name.equals("")) {
			portal.setName(sessionId, name);
		}
		if(!description.equals("")) {
			portal.setDescription(sessionId, description);
		}
		if(!image.equals("")) {
			portal.setPicture(sessionId, image);
		}
		sendNewsFeed();
	}

	/**
	 * Replaces all of the hex encoded characters in strings received through the url
	 * with the basic ascii characters accordingly.
	 * @param line The line to decode.
	 * @return The line in plain ascii.
	 */
	public static String replaceHex(String line) {
		line = line.replace("%21", "!");
		line = line.replace("%22", "\"");
		line = line.replace("%23", "#");
		line = line.replace("%24", "$");
		line = line.replace("%26", "&");
		line = line.replace("%27", "'");
		line = line.replace("%28", "(");
		line = line.replace("%29", ")");
		line = line.replace("%2A", "*");
		line = line.replace("%2B", "+");
		line = line.replace("%2C", ",");
		line = line.replace("%2D", "-");
		line = line.replace("%2E", ".");
		line = line.replace("%2F", "/");
		line = line.replace("%3A", ":");
		line = line.replace("%3F", "?");
		line = line.replace("%40", "@");
		line = line.replace("%5B", "[");
		line = line.replace("%5C", "\\");
		line = line.replace("%5D", "]");
		line = line.replace("%5E", "^");
		line = line.replace("%5F", "_");
		line = line.replace("%60", "`");
		line = line.replace("%7B", "{");
		line = line.replace("%7C", "|");
		line = line.replace("%7D", "}");
		line = line.replace("%7E", "~");
		return line;
	}

}
