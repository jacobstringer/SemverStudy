package githubScraper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.sql.*;
import org.json.*;

public class Scraper {
	//OkHttpClient client = new OkHttpClient();
	static final String token = "access_token="; // FILL IN
	static int since = 1;
	static int goal = 112_000_000;

	private static String readAll(Reader rd) {
		StringBuilder sb = new StringBuilder();
		int cp;
		try {
			while ((cp = rd.read()) != -1) {
				sb.append((char) cp);
			}
		} catch (IOException e) {}
		return sb.toString();
	}

	public static Object readJsonFromUrl(String url) throws IOException {
		InputStream is = new URL(url).openStream();
		String jsonText = "";
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			jsonText = readAll(rd);
			return new JSONArray(jsonText);
		} catch (JSONException e) {
			JSONObject json = new JSONObject(jsonText);

			// Token used up for the hour
			try {
				if (json.get("message").toString().contains("API rate limit exceeded")) {
					System.out.println(url.split("access_token=")[1]);
					System.out.println(Thread.currentThread().getName() + " has exceeded rate limits for the hour. Waiting for 5 minutes");
					try { // sleep for 5 minutes
						Thread.sleep(300_000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					} 
					return readJsonFromUrl(url);
				}
			} catch (JSONException e2) {}

			return json;
		} finally {
			is.close();
		}
	}

	public void gatherData(Connection c, PreparedStatement ps, FileSaver fs) {

		// Query GitHub API for projects
		while(since < goal) {
			JSONArray json;
			while (true) {
				try {
					json = (JSONArray)readJsonFromUrl("https://api.github.com/repositories?since="+ since + "&" + token);
					break;
				} catch (UnknownHostException e) {
					System.out.println(e.getMessage());
					System.out.println("Will try again in a minute");
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				} catch (IOException e) {}
			}

			// Each object is a single project
			for (Object obj: json) {
				JSONObject object = (JSONObject)obj;
				String url = object.get("html_url").toString();
				since = object.getInt("id");
				System.out.println(url);
				System.out.println(since);

				// Check for repeats
				try {
					PreparedStatement query = c.prepareStatement("SELECT * FROM dependencies WHERE url=?");
					query.setString(1, url);
					ResultSet rs = query.executeQuery();
					if (rs.isBeforeFirst()) {
						System.out.println(url + " is already in the db");
						continue;
					}
				} catch (SQLException e1) {
					e1.printStackTrace();
				}

				// Save build scripts if they exist in root folder of project
				long start = System.currentTimeMillis();
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
				start = System.currentTimeMillis();
				String languageurl = object.get("languages_url").toString();
				JSONObject languages;
				try {
					languages = (JSONObject)readJsonFromUrl(languageurl+"?"+token);
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
				start = System.currentTimeMillis();
				String user;
				try {
					String user_url = ((JSONObject)object.get("owner")).get("url").toString();
					user = ((JSONObject)readJsonFromUrl(user_url+"?"+token)).get("email").toString();
					if (user.equals("null")) {
						user = null;
					}
				} catch (IOException e) {
					continue;
				}

				// Save data (url, languages, build type, dependency information)
				start = System.currentTimeMillis();
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
					System.out.println(ps.toString());
				} catch (SQLException e) {
					e.printStackTrace();
				}	
				System.out.println(System.currentTimeMillis() - start);
			}
		}

		System.out.println(since);
	}
}
