package analysis_DependencyFinders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
public class ProducerParseFiles implements Runnable {

	private BlockingQueue <String[]> queue = null;
	public boolean stopped = false;
	public int count = 0;
	private int consumers;
	
	// CHANGE THESE FOUR VARIABLES ONLY
	private String zip = "E:/Build Scripts/pom.zip";
	private String ending = "pom\\.xml";
	private int start = 0;
	private int end = 50000;
	//private int end = Integer.MAX_VALUE; // Set to infinity to read until end of files

	public ProducerParseFiles(BlockingQueue<String[]> queue, int consumers) {
		super();
		this.queue = queue;
		this.consumers = consumers;
	}

	public void stop() {
		this.stopped = true;
	}

	@Override
	public void run() {
		String linesep = System.getProperty("line.separator");
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
				
				try(BufferedReader br = new BufferedReader(new InputStreamReader(zf.getInputStream(entry)))) {
					// Read in file into a single string
					String line;
					StringBuilder temp = new StringBuilder();
					while((line=br.readLine()) != null){
	                    temp.append(line);
	                    temp.append(linesep);
					}
					
					// Send to consumers
					String[] path = entry.getName().split("/");
					String url = "https://github.com/" + path[path.length-1].replaceAll(ending, "").replaceAll("\\+", "/");
					if (!(line = temp.toString()).isEmpty()) {
						try {
							queue.put(new String[]{line, path[0], url});
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Add Poison Pill
		try {
			for (int i = 0; i < consumers; i++)
				queue.put(new String[]{null, null, null});
		} catch (InterruptedException e1) {e1.printStackTrace();}

		stop();
		System.out.println("Finished");
	}
}
