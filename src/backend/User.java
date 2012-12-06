package backend;

import backend.DBPortal;
import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Represents the fields in the User table
 * @author Kyle Rogers
 *
 */
public class User {

	private static final String 
		PICTURE_RE 		= "%picture%",
		USERNAME_RE 	= "%username%",
		NAME_RE 		= "%name%",
		DESC_RE 		= "%description%";
	
	protected String name, username, email, description, picture;
	
	protected User(String username, String email, String desc, String picture, String name)
	{
		this.username 		= username;
		this.email 			= email;
		this.description 	= desc;
		this.picture 		= picture;
		this.name 			= name;
	}
	
	protected User(ResultSet rs) throws SQLException
	{
		this.username 		= rs.getString(1);
		this.email 			= rs.getString(2);
		this.description	= rs.getString(3);
		this.picture 		= rs.getString(4);
		this.name 			= rs.getString(5);
		description = description.replaceAll("<", "&#60");
		description = description.replaceAll(">", "&#62");
	}
	
	public String toString()
	{
		return this.name + "\n\t" + this.username + ", " + this.email + ", " + this.description + ", " + this.picture;
	}
	
	public String toHTML() throws FileNotFoundException
	{

		String userTempF = DBPortal.readFile("src" + DBPortal.SEP + "backend" + DBPortal.SEP + "HTMLTemplates" + DBPortal.SEP + "user_template.html");

		String html = userTempF.replaceAll(USERNAME_RE, username);
		html = html.replaceAll(NAME_RE, name);
		html = html.replaceAll(PICTURE_RE, picture);
		html = html.replaceAll(DESC_RE, description);
		return html;
	}
	
	public String toSearchHTML() throws FileNotFoundException
	{
		String html = DBPortal.readFile("src" + DBPortal.SEP + "backend" + DBPortal.SEP + "HTMLTemplates" + DBPortal.SEP + "result.html");
		html = html.replaceAll("%username%", username);
		html = html.replaceAll("%name%", name);
		html = html.replaceAll("%picture%", picture);
		html = html.replaceAll("%description%", description);
		return html;
	}
}
