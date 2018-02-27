package githubScraper.DependencyFinders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import githubScraper.Counter;

/**
 * A consumer takes lists of student instances and prints them by converting them into html document.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 */
public class ConsumerParseFiles implements Runnable {

	private BlockingQueue <String[]> queue = null;
	private Counter counter = null;
	private boolean stopped = false;
	private Connection c;
	private DependencyFinder df;
	public boolean done = false;

	public ConsumerParseFiles(BlockingQueue<String[]> queue, Counter counter, Connection c, GradleDependencyFinder df) {
		super();
		this.queue = queue;
		this.counter = counter;
		this.c = c;
		this.df = df;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		// Initialise classes	
		//AntDependencyFinder ant = new AntDependencyFinder();
		//GradleDependencyFinder gradle = new GradleDependencyFinder(c, out);
		//NPMDependencyFinder npm = new NPMDependencyFinder(c);
		//PomDependencyFinder pom = new PomDependencyFinder();
		//RakeDependencyFinder rake = new RakeDependencyFinder();
		
		while (!stopped) {
			try {
				// Take file, filetype, and url info information out of queue
				String[] file_string;
				if(!queue.isEmpty()){
					file_string = queue.take();
				} else {
					continue;
				}
				
				// Check for repeats
				try {
					PreparedStatement query = c.prepareStatement("SELECT * FROM npm WHERE url=?");
					query.setString(1, file_string[2]);
					ResultSet rs = query.executeQuery();
					if (rs.isBeforeFirst()) {
						continue;
					}
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				
				// Pass onto parsers to extract semantic version information
				//int[] dependencies = null;
				switch (file_string[1]) {
				//case "Rake": {dependencies = rake.findVersionData(file_string[0]); break;}
				case "Gradle": {df.findVersionData(file_string[0], file_string[2]); break;}
				//case "Build": {dependencies = ant.findVersionData(file_string[0]); break;}
				//case "Pom": {dependencies = pom.findVersionData(file_string[0]); break;}
				//case "Package": {npm.findVersionData(file_string[0], file_string[2]); break;}
				}
				
				counter.added_to_db();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			} 

		}
		done = true;
	}
}
