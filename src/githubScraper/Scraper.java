package githubScraper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.sql.*;
import org.json.*;

import githubScraper.DependencyFinders.*;

public class Scraper {
	//OkHttpClient client = new OkHttpClient();
	static final String token = "access_token=70580404d854ec52e85f6f93675e792eac2faccb";
	static int since = 1603118; // 1-16_061, 1_000_000-  (Gradle from 1_121_728)
	static int goal = 1700000;

	public static int[] extractDependencies(String[] file) {
		switch (file[0]) {
		//case "pom.xml": return new PomDependencyFinder().findVersionData(file[1]);
		//case "build.xml": return new AntDependencyFinder().findVersionData(file[1]);
		//case "package.json": return new NPMDependencyFinder().findVersionData(file[1]);
		//case "Rakefile.rb": return new RakeDependencyFinder().findVersionData(file[1]);
		//case "build.gradle": return new GradleDependencyFinder().findVersionData(file[1]);
		}
		return new int[2];
	}

	public static void processExistingBuildScripts(String[] info, String folder, Connection c, PreparedStatement ps) {
		for (File file: new File("../../githubSamples/"+folder+"/").listFiles()) {
			String name = file.getName();
			try {
				info[1] = readAll(new BufferedReader(new FileReader(file)));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				continue;
			}

			int[] dependencies = extractDependencies(info);
			String original_url = "https://github.com/" + name.replaceAll("+", "/");

			try {
				ps = c.prepareStatement("UPDATE dependencies SET sem_depend = ?, other_depend = ? WHERE url = '?';");
				ps.setInt(1, dependencies[1]);
				ps.setInt(2, dependencies[0]);
				ps.setString(3, original_url);
				ps.execute();
				System.out.println(ps.toString());
			} catch (SQLException e) {
				e.printStackTrace();
			}	
		}
	}

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
					System.out.println(Thread.currentThread().getName() + " has exceeded rate limits. Waiting for the next hour");
					int currentHour = new Date(System.currentTimeMillis()).getHours();
					while (new Date(System.currentTimeMillis()).getHours() == currentHour) {
						try { // sleep for 2 minutes
							Thread.sleep(120_000);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						} 
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
					fileInfo = fs.findSaveFile(url);
					if (fileInfo == null) {
						continue;
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.out.println(System.currentTimeMillis() - start);


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
				System.out.println(System.currentTimeMillis() - start);

				// Extract dependency information
				int[] dependencies = extractDependencies(fileInfo);

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
				System.out.println(System.currentTimeMillis() - start);

				// Save data (url, languages, build type, dependency information)
				start = System.currentTimeMillis();
				try {
					ps = c.prepareStatement(
							"INSERT INTO dependencies (url, lang1, lang2, buildtype, sem_depend, other_depend, users, ghid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
					ps.setString(1, url);
					ps.setString(2, first);
					ps.setString(3, second);
					ps.setString(4, fileInfo[0]);
					ps.setInt(5, dependencies[1]);
					ps.setInt(6, dependencies[0]);
					ps.setString(7, user);
					ps.setInt(8, since);
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

	/*public static void main(String[] args) {
		Scraper sc = new Scraper();
		FileSaver fs = new FileSaver();

		// DB Connection
		Connection c = null;
		PreparedStatement ps = null;
		try {
			Class.forName("org.postgresql.Driver");
			c = DriverManager.getConnection(
					"jdbc:postgresql://localhost:5432/BuildData", "postgres", "password");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName()+": "+e.getMessage());
			System.exit(0);
		}

		// Run queries
		sc.gatherData(c, ps, fs);

		// Process already collected build scripts, adjust info and folder
		// String[] info = {"build.xml", null};
		// String folder = "Build";
		// processExistingBuildScripts(info, folder, c, ps);

		// Close DB connection
		try {
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}*/

}
