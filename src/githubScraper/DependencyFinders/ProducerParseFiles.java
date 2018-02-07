package githubScraper.DependencyFinders;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * A producer produces lists of random student instances for processing.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 */
public class ProducerParseFiles implements Runnable {

	private BlockingQueue <String[]> queue = null;
	public boolean stopped = false;
	private String scripts = "D:\\Build Scripts\\";
	private int getFirstNScripts = 100000; // Change this if not doing full set
	private int count = 0;

	public ProducerParseFiles(BlockingQueue<String[]> queue) {
		super();
		this.queue = queue;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		// Open database records
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader("BuildFiles.csv"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			this.stopped = true;
		}
		
		while (!this.stopped && count < getFirstNScripts) {
			// Get one observation at a time out of the database file
			String[] info = null;
			try {
				String temp = in.readLine();
				if (temp == null) { // If file is finished
					break;
				}
				info = temp.split(",");
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Change url to file name as saved on computer
			String file = info[0].replaceFirst("https://github.com/", "").replace('/', '+');
			
			// Find the folder it is found in
			String type = null;
			switch(info[3]) {
			//case "Rakefile":
			//case "Rakefile.rb": {type = "Rake"; break;}
			//case "build.gradle": {type = "Gradle"; break;}
			//case "build.xml": {type = "Build"; break;}
			//case "pom.xml": {type = "Pom"; break;}
			case "package.json": {type = "Package"; break;}
			}
			
			if (type == null) {
				continue;
			}
			
			// Find the file which has the structure buildtype\first_character_of_file\file.extension
			BufferedReader in2;
			try {
				in2 = new BufferedReader(
						new FileReader(this.scripts + type + "\\" + file.charAt(0) + "\\" + file + "+" + info[3]));
			} catch (FileNotFoundException e) {
				//e.printStackTrace();
				System.err.println(file);
				continue;
			}
			
			// Read in file into a single string
			StringBuilder temp = new StringBuilder();
			String tempString = null;
			while(true) {
				try {tempString = in2.readLine();} catch (IOException e) {e.printStackTrace();}
				if (tempString == null) {
					break;
				} else {
					temp.append(tempString);
				}
			}
			
			// Send to consumers
			try {queue.put(new String[]{temp.toString(), type, info[0]});} catch (InterruptedException e1) {e1.printStackTrace();}
			
			// Close connection
			try {in2.close();} catch (IOException e) {e.printStackTrace();}
			count++;
		}
		// Close connection
		try {in.close();} catch (IOException e) {e.printStackTrace();}
		stop();
		System.out.println("Finished");
	}



}
