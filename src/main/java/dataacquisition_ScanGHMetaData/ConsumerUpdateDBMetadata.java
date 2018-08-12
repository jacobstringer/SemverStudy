package dataacquisition_ScanGHMetaData;

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
import org.json.JSONObject;

/**
 * A consumer takes lists of student instances and prints them by converting them into html document.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 * 
 * This consumer takes files off the queue, and routes them through to the correct build script logic for processing
 * Threadsafe
 */
public class ConsumerUpdateDBMetadata extends Consumer implements Runnable {

	private BlockingQueue <JSONObject> queue = null;
	private boolean stopped = false;
	private Connection c;
	public boolean done = false;

	public ConsumerUpdateDBMetadata(BlockingQueue<JSONObject> queue, Connection c) {
		super();
		this.queue = queue;
		this.c = c;
	}

	public void stop() {
		stopped = true;
	}

	private synchronized JSONObject takeFromQueue() {
		if(!queue.isEmpty()){
			try {
				return queue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} 
		return null;
	}

	private void storeCommit(String url, int id, boolean forked) {
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("UPDATE dependencies SET ghid=?, fork=? WHERE url=?");
			ps.setInt(1, id);
			ps.setBoolean(2, forked);
			ps.setString(3, url);
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	private boolean inDB(String url) {
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("SELECT * FROM dependencies WHERE url=?");
			ps.setString(1, url);
			ResultSet rs = ps.executeQuery();
			return rs.next();
		} catch (SQLException e) {
			return false;
		}
	}
	
	@Override
	public void run() {

		while (!stopped) {
			// Take url info information out of queue
			JSONObject meta = takeFromQueue();
			if(meta == null){
				continue;
			}

			String url_on_db = meta.getString("html_url");
			boolean forked = meta.getBoolean("fork");
			int ghid = meta.getInt("id");
			
			if (inDB(url_on_db))
				storeCommit(url_on_db, ghid, forked);
			
		}
		done = true;
	}
}
