package findLastCommits;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * A consumer takes lists of student instances and prints them by converting them into html document.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 * 
 * This consumer takes files off the queue, and routes them through to the correct build script logic for processing
 * Threadsafe
 */
public class ConsumerGetSaveCommitJSON extends Consumer implements Runnable {

	private BlockingQueue <String> queue = null;
	private boolean stopped = false;
	private Connection c;
	public boolean done = false;
	
	private final String path = "D:/Commits/";

	public ConsumerGetSaveCommitJSON(BlockingQueue<String> queue, Connection c) {
		super();
		this.queue = queue;
		this.c = c;
	}

	public void stop() {
		stopped = true;
	}

	private synchronized String takeFromQueue() {
		if(!queue.isEmpty()){
			try {
				String t = queue.take();
				if (t.equals("")) {
					stop();
					return null;
				}
				return t;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} 
		return null;
	}
	
	private boolean noEmail(String url) {
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("SELECT users FROM dependencies WHERE url=?");
			ps.setString(1, url);
			ResultSet rs = ps.executeQuery();
			if (rs.next() && rs.getString("users") == null) {
				return true;
			} else {
				return false;
			}
			
		} catch (SQLException e) {
			// System.err.println(e.getMessage());
			// Will trigger if the url is already in the DB, which will avoid duplicate information
			return true;
		}
	}
	
	private void saveJSON(String url, String json) {
		try {
			BufferedWriter out = new BufferedWriter(
					new FileWriter(
					new File((path+(url.replace("https://github.com/", "").replace("/", "+"))+".json"))));
			out.write(json);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean fileExists(String url) {
		try {
			return new File(path+(url.replace("https://github.com/", "").replace("/", "+"))+".json").exists();
		} catch (Exception e) {
			e.printStackTrace();
			return true;
		}
	}

	@Override
	public void run() {

		while (!stopped) {
			// Take url info information out of queue
			String url = takeFromQueue();
			if(url == null || noEmail(url) || fileExists(url)){
				continue;
			}

			String json = CollectFileFromURL.getFile(url);
			if (json.equals(""))
				continue;
			
			saveJSON(url, json);
			
		}
		done = true;
	}
}
