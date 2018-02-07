package githubScraper;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A producer produces lists of random student instances for processing.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 */
public class ProducerGitHub implements Runnable {

	public static final int MAX_LIST_SIZE = 100;
	private BlockingQueue <JSONObject> queue = null;
	private boolean stopped = false;
	private int since;
	private int goal;
	private String token;

	public ProducerGitHub(BlockingQueue<JSONObject> queue, int since, int goal, String token) {
		super();
		this.queue = queue;
		this.since = since;
		this.goal = goal;
		this.token = token;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		while (!this.stopped && this.since < this.goal) {
			List<JSONObject> list = new ArrayList<JSONObject>();

			// Get page of projects
			JSONArray json;
			while (true) {
				try {
					json = (JSONArray)Scraper.readJsonFromUrl("https://api.github.com/repositories?since="+ since + "&" + token);
					if (json.length() < 100) {
						for (Object temp: json) {
							System.out.println(((JSONObject)temp).toString());
						}
					}
					break;
				} catch (IOException e) {
					System.err.println(e.getMessage());
					System.out.println("Will try again in a minute");
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}

			for (Object jsonobject: json) {
				try {
					queue.put((JSONObject)jsonobject);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				since = ((JSONObject)jsonobject).getInt("id");
			}
		}
	}



}
