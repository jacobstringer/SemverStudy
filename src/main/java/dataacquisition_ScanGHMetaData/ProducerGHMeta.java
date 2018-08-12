package dataacquisition_ScanGHMetaData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A producer produces lists of random student instances for processing.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 * 
 * This producer reads in files for the consumer to process
 */
public class ProducerGHMeta implements Runnable {

	private BlockingQueue<JSONObject> queue = null;
	public boolean stopped = false;
	public int count = 0;
	private int since;
	private int goal;


	public ProducerGHMeta(BlockingQueue<JSONObject> queue, int since, int goal) {
		super();
		this.queue = queue;
		this.since = since;
		this.goal = goal;
	}

	public void stop() {
		this.stopped = true;
	}
	
	private void saveFile(String file) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(new File("E:/PaginatedGHRepositoryJSONs/"+since)));
			out.write(file);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (!this.stopped && this.since < this.goal) {
			// Get page of projects
			try {

				String file = CollectFileFromURL.getFile("https://api.github.com/repositories?since="+ since);
				saveFile(file);

				JSONArray json = new JSONArray(file);
				System.out.println(((JSONObject)json.get(0)).getInt("id"));

				if (json.length() < 100) {
					for (Object temp: json) {
						System.out.println(((JSONObject)temp).toString());
					}
				}
				
				for (Object jsonobject: json) {
					try {
						queue.put((JSONObject)jsonobject);
					} catch (InterruptedException | ClassCastException e) {
//						e.printStackTrace();
						continue;
					}
					since = ((JSONObject)jsonobject).getInt("id");
				}
				
			} catch (JSONException e) {
				System.err.println(e.getMessage());
			}
		}
	}
}
