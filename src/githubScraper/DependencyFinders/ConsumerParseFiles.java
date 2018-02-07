package githubScraper.DependencyFinders;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;

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

	public ConsumerParseFiles(BlockingQueue<String[]> queue, Counter counter, Connection c) {
		super();
		this.queue = queue;
		this.counter = counter;
		this.c = c;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		// Initialise classes
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("JSONtypes.txt"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			stopped = true;
		}
		
		AntDependencyFinder ant = new AntDependencyFinder();
		GradleDependencyFinder gradle = new GradleDependencyFinder();
		NPMDependencyFinder npm = new NPMDependencyFinder(c, out);
		PomDependencyFinder pom = new PomDependencyFinder();
		RakeDependencyFinder rake = new RakeDependencyFinder();
		
		while (!stopped) {
			try {
				// Take file, filetype, and url info information out of queue
				String[] file_string;
				if(!queue.isEmpty()){
					file_string = queue.take();
				} else {
					continue;
				}
				
				// Pass onto parsers to extract semantic version information
				int[] dependencies = null;
				switch (file_string[1]) {
				//case "Rake": {dependencies = rake.findVersionData(file_string[0]); break;}
				//case "Gradle": {dependencies = gradle.findVersionData(file_string[0]); break;}
				//case "Build": {dependencies = ant.findVersionData(file_string[0]); break;}
				//case "Pom": {dependencies = pom.findVersionData(file_string[0]); break;}
				case "Package": {dependencies = npm.findVersionData(file_string[0], file_string[2]); break;}
				}
				
				/*
				if (dependencies == null) {
					System.out.println(file_string[0] + " " + file_string[1] + " " + file_string[2] + " did not have the correct dependencies and was skipped");
					continue;
				}
				
				
				// Add information back to DB
				PreparedStatement ps = null;
				try {
					ps = c.prepareStatement("UPDATE dependencies SET sem_depend = ?, other_depend = ? WHERE url = ?");
					ps.setInt(1, dependencies[1]);
					ps.setInt(2, dependencies[0]);
					ps.setString(3, file_string[2]);
					ps.execute();
				} catch (SQLException e) {
					System.out.println(ps.toString());
					e.printStackTrace();
				}	*/
				
				counter.added_to_db();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		try {
			out.close();
			System.out.println("file closed");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
