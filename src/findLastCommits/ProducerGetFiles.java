package findLastCommits;

import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.BlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A producer produces lists of random student instances for processing.
 * It implements Runnable to enable multiple producers to run in parallel using threads.
 * 
 * This producer reads in files for the consumer to process
 */
public class ProducerGetFiles implements Runnable {

	private BlockingQueue <String> queue = null;
	public boolean stopped = false;
	public int count = 0;
	private int consumers;
	
	// CHANGE THESE FOUR VARIABLES ONLY
	private String[] ziparray = new String[]{"E:/Build Scripts/gradle.zip", "E:/Build Scripts/pom.zip", "E:/Build Scripts/package.zip", "E:/Build Scripts/rake.zip"};
	private int start = 18_608_000;
	//private int end = 10_090_100; 
	private int end = Integer.MAX_VALUE; // Set to infinity to read until end of files

	public ProducerGetFiles(BlockingQueue<String> queue, int consumers) {
		super();
		this.queue = queue;
		this.consumers = consumers;
	}

	public void stop() {
		this.stopped = true;
	}

	@Override
	public void run() {
		for (String zip: ziparray) {
			try (ZipFile zf = new java.util.zip.ZipFile(zip)) {
				for (Enumeration<? extends ZipEntry> entries = zf.entries(); entries.hasMoreElements() && !this.stopped;) {				
					// Count and skip if below min
					if (++this.count % 1000 == 0)
						System.out.println("Read in file: " + this.count);
					if (this.count < this.start)
						continue;
					if (this.count > this.end)
						break;
					
					// Read in file and place on queue
					ZipEntry entry = entries.nextElement();
					if (entry.isDirectory())
						continue;
					
					String[] path = entry.getName().split("/");
					
					path = path[path.length-1].split("\\+");
					String url = "https://github.com/";
					try {
						for (int i = 0; i < path.length - 2; i++)
							url += path[i] + "/";
						url += path[path.length-2];
					} catch (ArrayIndexOutOfBoundsException e) {}
					
					try {
						queue.put(url);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// Add Poison Pill
		try {
			for (int i = 0; i < consumers; i++)
				queue.put("");
		} catch (InterruptedException e1) {e1.printStackTrace();}

		stop();
		System.out.println("Finished");
	}
}
