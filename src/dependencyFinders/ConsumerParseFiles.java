package dependencyFinders;

import java.io.BufferedWriter;
import java.io.Writer;
import java.sql.Connection;
import java.util.concurrent.BlockingQueue;
import githubScraper.Counter;

/**
 * A consumer takes lists of student instances and prints them by converting them into html document.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 * 
 * This consumer takes files off the queue, and routes them through to the correct build script logic for processing
 * Threadsafe
 */
public class ConsumerParseFiles implements Runnable {

	private BlockingQueue <String[]> queue = null;
	private Counter counter = null;
	private boolean stopped = false;
	private Connection c;
	private Writer out;
	public boolean done = false;
	public int notfound = 0;

	public ConsumerParseFiles(BlockingQueue<String[]> queue, Counter counter, Connection c, BufferedWriter out) {
		super();
		this.queue = queue;
		this.counter = counter;
		this.c = c;
		this.out = out;
	}

	public void stop() {
		stopped = true;
	}

	private synchronized String[] takeFromQueue() {
		if(!queue.isEmpty()){
			try {
				String[] t = queue.take();
				if (t[0] == null && t[1] == null && t[2] == null) {
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

	@Override
	public void run() {
		// Initialise classes	
		//AntDependencyFinder ant = new AntDependencyFinder();
		GradleDependencyFinderSubFilesInDir gradle = new GradleDependencyFinderSubFilesInDir(c, out);
		NPMDependencyFinder npm = new NPMDependencyFinder(c);
		PomDependencyFinder pom = new PomDependencyFinder(c, out);
		//RakeDependencyFinder rake = new RakeDependencyFinder();

		while (!stopped) {
			// Take file, filetype, and url info information out of queue
			String[] file_string = takeFromQueue();
			if(file_string == null){
				continue;
			}

			// Check for repeats
			/* try {
				PreparedStatement query = c.prepareStatement("SELECT * FROM npm WHERE url=?");
				query.setString(1, file_string[2]);
				ResultSet rs = query.executeQuery();
				if (rs.isBeforeFirst()) {
					continue;
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			} */

			// Pass onto parsers to extract semantic version information
			switch (file_string[1]) {
			//case "rake": {dependencies = rake.findVersionData(file_string[0]); break;}
			case "gradle": {gradle.findVersionData(file_string[0], file_string[2]); break;}
			//case "build": {dependencies = ant.findVersionData(file_string[0]); break;}
			case "pom": {pom.findVersionData(file_string[0], file_string[2]); break;}
			case "package": {npm.findVersionData(file_string[0], file_string[2]); break;}
			}

			counter.added_to_db();
			notfound = gradle.unfoundVariables;
		}
		done = true;
	}
}
