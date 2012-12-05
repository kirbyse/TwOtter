package server;


import java.io.*;
import java.net.*;
import java.sql.SQLException;

import backend.DBPortal;

public class HttpHandler extends java.lang.Thread {
	DBPortal portal = new DBPortal();
	Socket client;
	OutputStream os=null;
	String sessionId;
	String DEFAULT_ID = "00000000000000000000";

	public HttpHandler(Socket client,int cnt) {
		this.client=client;
		sessionId = DEFAULT_ID;
	}

	/* send 404 */
	public void send404() throws IOException {
		sendResponse(404,"404 - Invalid URL","text/html","<html><body>Error - invalid url</body></html>".getBytes());
	}

	/* send 500 */
	public void send500() throws IOException {
		sendResponse(500,"500 - Invalid Server Request","text/html","<html><body>Error - invalid Request</body></html>".getBytes());
	}


	/*
	send response
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

	/*
	send response HEADER
	 */
	public void sendResponseHeader(int code, String status, String type) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("HTTP/1.1 " + code + " " + status + "\r\n");
		sb.append("Connection: close\r\n");
		sb.append("Content-Type: " + type + "\r\n");
		sb.append("\r\n");	//dont' forget blank line
		os.write(sb.toString().getBytes());
	}

	public String getContentType(String URL) {
		//get extension
		System.err.println(URL);
		String ext[] = URL.split("\\.");
		System.err.println(ext.length);
		String e = ext[ext.length-1].toLowerCase();
		if ("txt".equals(e)) 
			return "text/txt";
		else if("html".equals(e) || "htm".equals(e))
			return "text/html";
		else if ("jpg".equals(e))
			return "image/jpg";
		else if ("gif".equals(e))
			return "image/gif";
		else
			return "text/txt";
	}

	/*
	Security check url
	 */
	public String testURL(String url) {
		if (url.contains("..")) // do not allow path to move above
			return null;
		else
			return url;
	}

	public String getRequest() throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
		String get = br.readLine();
		System.out.println(get);
		if (get==null)
			throw new IOException("null string");
		String line = "empty";
		while (!line.equals("")) {
			line = br.readLine();
			System.out.println(line);
			line = line.toLowerCase();
			if(line.contains("session=")) { //If the HTTP request contains a cookie for sessionId
				sessionId = line.substring(line.indexOf("session=")+8);
				System.out.println("sessionId = " + sessionId);
			}
		}
		String parts[] =  get.split(" ");
		if (parts.length != 3 || !parts[0].toLowerCase().equals("get") || !parts[2].toLowerCase().startsWith("http/"))
		{
			send500();
			return null;
		}
		System.out.println("URL: " + parts[1]);
		return parts[1];
	}

	public void run() {
		System.err.println("handler");
		try {
			os = client.getOutputStream();
			String URL = getRequest();
			//URL = URL.toLowerCase();
			if (URL!= null) {
				if(sessionId.equals(DEFAULT_ID)) {
					if (URL.contains("username=") && URL.contains("password=")) {
						//User just submitted log in information
						getLogin(URL);
					}
					else {
						//By default send user to log in page if they have not logged
						sendLogin();
					}
				}
				else {
					if (URL.equals("/twotter") || URL.equals("/") || URL.equals("/home")) {
						sendNewsFeed();
					}
					else if (URL.contains("username=") && URL.contains("password=")) {
						//User just submitted log in information
						getLogin(URL);
					}
					else if (URL.equals("/makeaprofile")) {
						//sendMakeAProfile();
					}
					else if (URL.startsWith("/makeaprofile")) {
						//User made profile
						//getMakeAProfile();
					}
					else if (URL.contains("post=")) {
						String[] parts = URL.split("post=");
						String post = parts[1];
						post = post.replace("+", " ");
						portal.createPost(post,sessionId);
						sendNewsFeed();
					}
					else if(URL.equals("/editprofile")) {
						sendEditProfile();
					}
					else if(URL.startsWith("/editprofile") && URL.contains("name=") && URL.contains("description=") && URL.contains("image=")) {
						//User Edited Profile
						getEditProfile(URL);
					}
					else if(URL.equals("/logout")) {
						sessionId = DEFAULT_ID;
						sendLogin();
					}
					else if(portal.userExists(URL.substring(1))) {
						userProfile(URL.substring(1));
					}
					else {
						send404();
					}
				}
			}
			os.close();
		} catch (Exception err) {
			err.printStackTrace();
		}
	}

	public void userProfile(String username) throws IOException, SQLException { //Get someone else's Profile
		try {
			String body = portal.getProfileHTML(username);
			sendResponse(200,"OK","text/html",body.getBytes());
		} catch (FileNotFoundException err) {
			send404();
		}
	}

	public void sendProfile() throws SQLException, IOException {
		String body = portal.getProfileHTML_SessionID(sessionId);
		sendResponse(200,"OK","text/html",body.getBytes());
	}

	public void getLogin(String URL) throws IOException, SQLException {
		String responses[] = URL.split("=");
		String username = responses[1].substring(0, responses[1].length()-9);
		String password = responses[2];
		System.out.println(username);
		System.out.println(password);
		if(!username.equals("") && !password.equals("")) {
			if(portal.checkLogin(username, password)) { //Log in was successful
				sendLoginNewsFeed(username);
			} else { //Login was unsuccessful
				sendLoginError();
			}
		} else {
			sendLoginError();
		}

	}

	//Newsfeed that is sent when the user first logs on, sets the sessionId cookie for the client browser
	//establishing session control until the user logs out.
	public void sendLoginNewsFeed(String username) throws IOException, SQLException {
		sessionId = "12345678901234567890";
		String body = portal.getNewsFeedHTML(username);
		sendCookieResponse(200,"OK","text/html",body.getBytes());
	}

	public void sendNewsFeed() throws IOException, SQLException {
		String username = portal.getUsernameByID(sessionId); //This method doesn't work
		String body = portal.getNewsFeedHTML(username);
		sendResponse(200,"OK","text/html",body.getBytes());
	}

	public void getEditProfile(String URL) throws IOException { //Justin will change this text
		String responses[] = URL.split("=");
		String name = responses[1].substring(0, responses[1].length()-6);
		String image = responses[2].substring(0,responses[2].length()-12);
		String description = responses[3];
		System.out.println(name);
		System.out.println(image);
		System.out.println(description);
	}

	public void sendLogin() throws IOException { //Justin will change this text
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Login</title></head><body>");
		sb.append("<h1>Login</h1><br>");
		sb.append("<form method='get'>");
		sb.append("Username: <input type='text' name='username'><br>");
		sb.append("Password: <input type='text' name='password'><input type='submit'>");
		sb.append("</form></body></html>");
		sendCookieResponse(200,"OK","text/html",sb.toString().getBytes());
	}

	public void sendLoginError() throws IOException { //Justin will change this text
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Login Error</title></head><body>");
		sb.append("<h1>Login Error</h1><br>");
		sb.append("<form method='get'>");
		sb.append("Username: <input type='text' name='username'><br>");
		sb.append("Password: <input type='text' name='password'><input type='submit'>");
		sb.append("</form></body></html>");
		sendResponse(200,"OK","text/html",sb.toString().getBytes());
	}

	public void sendEditProfile() throws IOException { //Just will change this text
		StringBuffer sb = new StringBuffer();
		sb.append("<html><head><title>Edit Your Profile</title></head><body>");
		sb.append("<h1>Edit your Profile</h1><br>");
		sb.append("<form method='get'>");
		sb.append("Enter your new name: <input type='text' name='name'><br>");
		sb.append("Enter your new image (.jpg or .gif only): <input type='file' name='image'><br>");
		sb.append("Enter your new profile description (up to 140 characters): <input type='text' name='description' size=140><input type='submit'>");
		sb.append("</form></body></html>");
		sendResponse(200,"OK","text/html",sb.toString().getBytes());
	}
}
