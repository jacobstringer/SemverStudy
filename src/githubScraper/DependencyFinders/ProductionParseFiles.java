package githubScraper.DependencyFinders;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import githubScraper.Counter;
import githubScraper.ProductionMBean;

public class ProductionParseFiles implements ProductionMBean {
	private static final int PRODUCER_COUNT = 1;
	private static final int CONSUMER_COUNT = 10;
	private static final int BUFFER_SIZE = 1000;

	private static Connection c;

	public ProductionParseFiles() {
		super();
		// register mbean for monitoring
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer(); 
		try {
			ObjectName name = new ObjectName("DependencyParser:type=Production,id=1");
			mbs.registerMBean(this, name); 
		} catch (Exception x) {
			System.err.println("Registering mbean for monitoring failed");
		}
	}

	private List<ProducerParseFiles> producers = new ArrayList<ProducerParseFiles>();
	private List<ConsumerParseFiles> consumers = new ArrayList<ConsumerParseFiles>();
	private BlockingQueue <String[]> queue = new ArrayBlockingQueue<String[]>(BUFFER_SIZE);
	private Counter jobCounter = new Counter("processed jobs");
	private GradleDependencyFinder gradle;
	private BufferedWriter out = null;

	public static void main(String[] args) {
		new ProductionParseFiles().start();
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
		
		// Log
		try {
			out = new BufferedWriter(new FileWriter("Tests.txt"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Logic
		gradle = new GradleDependencyFinder(c, out);
		
		// start producers
		for (int i=0;i<PRODUCER_COUNT;i++) {
			ProducerParseFiles producer = new ProducerParseFiles(queue);
			Thread thread = new Thread(producer);
			thread.setName("producer - " + (1)); // name to facilitate analysis with VisualVM or similar tool
			thread.start();
			producers.add(producer);
		}

		// start consumers
		for (int i=0;i<CONSUMER_COUNT;i++) {
			ConsumerParseFiles consumer = new ConsumerParseFiles(queue, jobCounter, c, gradle);
			Thread thread2 = new Thread(consumer);
			thread2.setName("consumer - " + i); // name to facilitate analysis with VisualVM or similar tool
			thread2.start();
			consumers.add(consumer);
		}

		while (true) {
			// wait to give consumer a chance to finish
			try {
				if (!producers.get(0).stopped) {
					Thread.sleep(1000);
				} else {
					Thread.sleep(1000);
					stop();
				}

			} catch (InterruptedException e) {}
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
		for (ProducerParseFiles producer:producers) {
			producer.stop();
		}
		// wait to see whether there are still jobs, if none left, stop consumers
		// now we can safely stop consumers
		for (ConsumerParseFiles consumer:consumers) {
			while (!consumer.done){
				System.out.print("w");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
		}
		
		// Info
		try {
			out.flush();
			out.close();
			System.out.println("file closed");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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