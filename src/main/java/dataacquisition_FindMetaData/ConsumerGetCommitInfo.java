package dataacquisition_FindMetaData;

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
public class ConsumerGetCommitInfo extends Consumer implements Runnable {

	private BlockingQueue <String> queue = null;
	private boolean stopped = false;
	private Connection c;
	public boolean done = false;
	
	private final Pattern p = Pattern.compile("\\d+-\\d+-\\d+");

	public ConsumerGetCommitInfo(BlockingQueue<String> queue, Connection c) {
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

	private String getFinalCommit(String json) {
		try {
			return new JSONArray(json).getJSONObject(0).getJSONObject("commit").getJSONObject("author").getString("date");
		} catch (JSONException e) {
			System.err.println(json);
			return "";
		}
	}

	private void storeCommit(String url, String finalcom) {
		// Add to database, first to gradlefiles, then the individual results to gradleentries
		// finalcom looks like this = "2014-02-03T19:24:07Z"
		// url in same format as DB
		
		Matcher m = p.matcher(finalcom);
		if (!m.find()) {
			System.err.println(url + " could not find a date in " + finalcom);
			return;
		} 
		
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("UPDATE dependencies SET latestcommit=? WHERE url=?");
			ps.setDate(1, Date.valueOf(m.group()));
			ps.setString(2, url);
			ps.execute();
		} catch (SQLException e) {
			// System.err.println(e.getMessage());
			// Will trigger if the url is already in the DB, which will avoid duplicate information
		}
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

	@Override
	public void run() {

		while (!stopped) {
			// Take url info information out of queue
			String url = takeFromQueue();
			if(url == null || noEmail(url)){
				continue;
			}

			String json = CollectFileFromURL.getFile(url);
			if (json.equals(""))
				continue;
			
			String finalcommit = getFinalCommit(json);
			storeCommit(url, finalcommit);
			
		}
		done = true;
	}
}
