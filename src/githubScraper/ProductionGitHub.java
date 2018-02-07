package githubScraper;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.json.JSONObject;

public class ProductionGitHub implements ProductionMBean {
	private static final int PRODUCER_COUNT = 1;
	private static final int CONSUMER_COUNT = 80;
	private static final int BUFFER_SIZE = 10000;

	private static final int SINCE = 16045;
	private static final int GOAL = 1532460;
	
	private static final String[] tokens = {
			
	};
	
	private static Connection c;

	public ProductionGitHub() {
		super();
		// register mbean for monitoring
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer(); 
		try {
			ObjectName name = new ObjectName("githubScraper:type=Production,id=1");
			mbs.registerMBean(this, name); 
		} catch (Exception x) {
			System.err.println("Registering mbean for monitoring failed");
		}
	}

	private List<ProducerGitHub> producers = new ArrayList<ProducerGitHub>();
	private List<ConsumerGitHub> consumers = new ArrayList<ConsumerGitHub>();
	private BlockingQueue <JSONObject> queue = new ArrayBlockingQueue<JSONObject>(BUFFER_SIZE);
	private Counter jobCounter = new Counter("processed jobs");

	public static void main(String[] args) {
		new ProductionGitHub().start();
	}

	public void start() {

		// DB
		try {
			Class.forName("org.postgresql.Driver");
			c = DriverManager.getConnection(
					"jdbc:postgresql://localhost:5432/BuildData", "postgres", "password");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName()+": "+e.getMessage());
			System.exit(0);
		}

		// start producers
		//for (int i=0;i<PRODUCER_COUNT;i++) {
			ProducerGitHub producer = new ProducerGitHub(queue, SINCE, GOAL, "access_token=d87e2041b0983ce9973d4e64deec92483bbbe676");
			Thread thread = new Thread(producer);
			thread.setName("producer - " + (1)); // name to facilitate analysis with VisualVM or similar tool
			thread.start();
			producers.add(producer);
		//}

		// start consumers
		for (int i=0;i<tokens.length*5;i++) {
			ConsumerGitHub consumer = new ConsumerGitHub(queue, jobCounter, c, tokens[i%tokens.length]);
			Thread thread2 = new Thread(consumer);
			thread2.setName("consumer - " + i); // name to facilitate analysis with VisualVM or similar tool
			thread2.start();
			consumers.add(consumer);
		}

	}

	@Override
	public int getBufferSize() {
		return queue.size();
	}
	@Override
	public int getMaxBufferSize() {
		return BUFFER_SIZE;
	}
	@Override
	public int getProcessedJobCount() {
		return jobCounter.getValue();
	}

	// this method can be invoked from a JMX monitor such as VisualVM !
	@Override
	public void stop() {
		// stop producers, let consumers finish all jobs in the buffer
		for (ProducerGitHub producer:producers) {
			producer.stop();
		}
		// wait to see whether there are still jobs, if none left, stop consumers
		while (queue.size()>0) {
			// wait to give consumer a chance to finish
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
		// now we can safely stop consumers
		for (ConsumerGitHub consumer:consumers) {
			consumer.stop();
		}

		// Close DB connection
		try {
			c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		System.out.println("stopped production");
		System.exit(0);
	}

}