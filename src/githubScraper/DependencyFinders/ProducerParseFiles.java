package githubScraper.DependencyFinders;

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
	private String type = "gradle";
	private String zip = "D:/Build Scripts/"+type+".zip";
	private int count = 0;
	private int start = 0;
	//private int until = 100;

	public ProducerParseFiles(BlockingQueue<String[]> queue) {
		super();
		this.queue = queue;
	}

	public void stop() {
		stopped = true;
	}

	@Override
	public void run() {
		String linesep = System.getProperty("line.separator");
		try (ZipFile zf = new java.util.zip.ZipFile(zip)) {
			for (Enumeration<? extends ZipEntry> entries = zf.entries(); entries.hasMoreElements();) {
				// Count and skip if below min
				if (++count % 10000 == 0)
					System.out.println("Read in file: " + count);
				if (count < start)
					continue;
				
				// Read in file and place on queue
				ZipEntry entry = entries.nextElement();
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
					String url = "https://github.com/" + path[path.length-1].replaceAll("+", "/");
					if ((line = temp.toString()) != null) {
						try {
							queue.put(new String[]{line, type, url});
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
			for (int i = 0; i < 100; i++)
				queue.put(new String[]{null, null, null});
		} catch (InterruptedException e1) {e1.printStackTrace();}

		stop();
		System.out.println("Finished");
	}
}
