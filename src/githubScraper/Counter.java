package githubScraper;

public class Counter {

	private int value = 0;
	private int db = 0;
	private String name = null;
	private long timestamp = System.currentTimeMillis();
	private static final int LOG_INTERVAL = 10000;
	
	public Counter(String name) {
		super();
		this.name = name;
	}

	
	public synchronized void increase () {
		value++;
		if (value%LOG_INTERVAL==0) {
			long now = System.currentTimeMillis();
			long diff = now-timestamp;
			timestamp = now;
			System.out.println(name+": it took " + diff + " ms to process " + LOG_INTERVAL + " jobs");
			
		}
	}
	public synchronized void added_to_db() {
		db++;
		if (db%LOG_INTERVAL==0) {
			long now = System.currentTimeMillis();
			long diff = now-timestamp;
			timestamp = now;
			System.out.println(name+": it took " + diff + " ms to add " + LOG_INTERVAL + " entries to the DB");
		}
	}
	
	public synchronized int getValue() {
		return value;
	}

}
