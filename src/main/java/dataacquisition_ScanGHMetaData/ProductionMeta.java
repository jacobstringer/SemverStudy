package dataacquisition_ScanGHMetaData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.json.JSONObject;

import dataacquisition_GithubScraper.Counter;
import dataacquisition_GithubScraper.ProductionMBean;

public class ProductionMeta implements ProductionMBean {
	private static final int PRODUCER_COUNT = 5;
	private static final int CONSUMER_COUNT = 10;
	private static final int BUFFER_SIZE = 10000;

	public ProductionMeta() {
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

	private List<ProducerGHMeta> producers = new ArrayList<>();
	private List<Consumer> consumers = new ArrayList<>();
	private List<Connection> connections = new ArrayList<>();
	private BlockingQueue <JSONObject> queue = new ArrayBlockingQueue<>(BUFFER_SIZE);
	private Counter jobCounter = new Counter("processed jobs");

	public static void main(String[] args) {
		new ProductionMeta().start();
	}

	public void start() {

		// DB
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e2) {
			e2.printStackTrace();
		}
		
		// start producers
		ArrayList<Integer> missing = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new FileReader(new File("data/emails.csv")))) {
			String temp;
			in.readLine();
			int last = -100;
			while ((temp = in.readLine()) != null) {
				try {
					int cur = Integer.parseInt(temp.split(",")[1]);
					if (last + 100 > cur) {
						continue;
					}
					last = cur;
					missing.add(cur-1);
				} catch (ClassCastException e) {}
			}
		} catch (IOException e) {}

		ExecutorService pool = Executors.newFixedThreadPool(10);
		for (int i=0;i<missing.size();i++) {
			if (missing.get(i) == 0) {
				continue;
			}
			Runnable task = new ProducerGHMeta(queue, missing.get(i), missing.get(i)+1);
			pool.execute(task);
		}

		// start consumers
		for (int i=0;i<CONSUMER_COUNT;i++) {
			Consumer consumer = null;
			try {
				connections.add(DriverManager.getConnection(
						"jdbc:postgresql://localhost:5432/BuildData", "postgres", "password"));
				consumer = new ConsumerUpdateDBMetadata(queue, connections.get(connections.size()-1));
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Thread thread2 = new Thread(consumer);
			thread2.setName("consumer - " + i); // name to facilitate analysis with VisualVM or similar tool
			thread2.start();
			consumers.add(consumer);
		}
		
		while (true) {
			// wait to give consumer a chance to finish
			try {
				Thread.sleep(1000);
				if (producers.get(0).stopped) {
					stop();
				}
			} catch (InterruptedException e1) {}
		}

	}

	public int getBufferSize() {
		return queue.size();
	}
	public int getMaxBufferSize() {
		return BUFFER_SIZE;
	}
	public int getProcessedJobCount() {
		return jobCounter.getValue();
	}

	// this method can be invoked from a JMX monitor such as VisualVM !
	public void stop() {
		// stop producers, let consumers finish all jobs in the buffer
		for (ProducerGHMeta producer:producers) {
			producer.stop();
		}
		// wait to see whether there are still jobs, if none left, stop consumers
		// now we can safely stop consumers
		int q = queue.size();
		int times = 0;
		while (!queue.isEmpty()){
			System.out.print("s");
			System.out.print(queue.size());
			if (q == queue.size()) {
				if (++times > 15) {
					break;
				}
			} else {
				q = queue.size();
				times = 0;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}

		// Close DB connection
		try {
			for (Connection c: connections)
				c.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		System.out.println("stopped production");
		System.exit(0);
	}

}