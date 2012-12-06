package backend;

import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Represents the fields in the Post/Posted tables
 * @author Kyle Rogers
 *
 */
public class Post {

	private String postBy, postedBy, message, postTime, picture;
	private Long postID;
	private static final String 	POSTED_BY_RE = "%postedUser%",
									POST_BY_RE = "%postUser%",
									MESSAGE_RE = "%message%",
									PICTURE_RE = "%picture%",
									POST_ID_RE = "%postID%",
									POST_TIME_RE = "%timestamp%";
	
	
	
	public Post(String postBy, String postedBy, String message, String postTime, long postID, String picture)
	{
		this.postBy 	= postBy;
		this.postedBy	= postedBy;
		this.message 	= message;
		this.picture 	= picture;
		this.postID 	= postID;
		this.postTime 	= postTime;
		message = message.replaceAll("<", "&#60");
		message = message.replaceAll(">", "&#62");
	}
	
	protected Post(ResultSet rs) throws SQLException
	{
		this.postID 	= rs.getLong(1);
		this.postedBy 	= rs.getString(2);
		this.postBy 	= rs.getString(3);
		this.postTime 	= rs.getString(4);
		this.message 	= rs.getString(5);
		this.picture 	= rs.getString(6);
		message = message.replaceAll("<", "&#60");
		message = message.replaceAll(">", "&#62");
	}
	
	@Override
	public String toString()
	{
		return postBy + " posted:\n\t" + message + "\n\tat " + postTime + ((postedBy.equals(postBy)) ? "":"\n\tReposted by " + postedBy);
	}
	
	public String toHTML() throws FileNotFoundException
	{
		
		String postTempF = DBPortal.readFile("src" + DBPortal.SEP + "backend" + DBPortal.SEP + "HTMLTemplates" + DBPortal.SEP + "post_template.html");
		String html = postTempF.replaceFirst(POSTED_BY_RE, (postBy.equals(postedBy)) ? "" : "Reposted by " + postedBy);
		html = html.replaceAll(POST_ID_RE, postID.toString());
		html = html.replaceAll(POST_BY_RE, postBy);
		html = html.replaceFirst(MESSAGE_RE, message);
		html = html.replaceFirst(POST_TIME_RE, postTime);
		html = html.replaceFirst(PICTURE_RE, picture);
		return html;
	}
	
	public String getPostBy() {
		return postBy;
	}
	
}
