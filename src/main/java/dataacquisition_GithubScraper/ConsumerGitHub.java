package dataacquisition_GithubScraper;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;

import org.json.JSONObject;

/**
 * A consumer takes lists of student instances and prints them by converting them into html document.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 */
public class ConsumerGitHub implements Runnable {

	private BlockingQueue <JSONObject> queue = null;
	private Counter counter = null;
	private boolean stopped = false;
	private Connection c;
	private final String token;

	public ConsumerGitHub(BlockingQueue<JSONObject> queue, Counter counter, Connection c, String token) {
		super();
		this.queue = queue;
		this.counter = counter;
		this.c = c;
		this.token = token;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		//synchronized (Producer.class) {
		while (!stopped) {
			try {
				JSONObject object = queue.take();

				String url = object.get("html_url").toString();
				int since = object.getInt("id");
				counter.increase();

				// Check for repeats
				try {
					PreparedStatement query = c.prepareStatement("SELECT * FROM dependencies WHERE url=?");
					query.setString(1, url);
					ResultSet rs = query.executeQuery();
					if (rs.isBeforeFirst()) {
						// The following section is only temporary
						String user;
						try {
							String user_url = ((JSONObject)object.get("owner")).get("url").toString();
							user = ((JSONObject)Scraper.readJsonFromUrl(user_url+"?"+token)).get("email").toString();
							if (user.equals("null")) {
								user = null;
							}
						} catch (IOException e) {
							continue;
						}
						
						PreparedStatement ps = null;
						ps = c.prepareStatement("UPDATE dependencies SET ghid=?, users=? WHERE url=?");
						ps.setInt(1, since);
						ps.setString(2, user);
						ps.setString(3, url);
						ps.execute();
						//System.out.println(ps.toString());
						// END TEMP SECTION
						
						continue;
					}
				} catch (SQLException e1) {
					e1.printStackTrace();
				}

				// Save build scripts if they exist in root folder of project
				String[] fileInfo = {null, null};
				try {
					fileInfo = FileSaver.findSaveFile(url);
					if (fileInfo == null) {
						continue;
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				// Language information
				String languageurl = object.get("languages_url").toString();
				JSONObject languages;
				try {
					languages = (JSONObject)Scraper.readJsonFromUrl(languageurl+"?"+token);
				} catch (IOException e) {
					continue;
				}
				String first = null, second = null;
				Iterator<String> keys = languages.keys();
				try {
					first = keys.next();
					second = keys.next();
				} catch (Exception e) {}

				// User email
				String user;
				try {
					String user_url = ((JSONObject)object.get("owner")).get("url").toString();
					user = ((JSONObject)Scraper.readJsonFromUrl(user_url+"?"+token)).get("email").toString();
					if (user.equals("null")) {
						user = null;
					}
				} catch (IOException e) {
					continue;
				}

				// Save data (url, languages, build type, dependency information)
				PreparedStatement ps = null;
				try {
					ps = c.prepareStatement(
							"INSERT INTO dependencies (url, lang1, lang2, buildtype, users, ghid) VALUES (?, ?, ?, ?, ?, ?)");
					ps.setString(1, url);
					ps.setString(2, first);
					ps.setString(3, second);
					ps.setString(4, fileInfo[0]);
					ps.setString(5, user);
					ps.setInt(6, since);
					ps.execute();
					//System.out.println(ps.toString());
				} catch (SQLException e) {
					System.out.println(ps.toString());
					e.printStackTrace();
				}	

				counter.added_to_db();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}
}
