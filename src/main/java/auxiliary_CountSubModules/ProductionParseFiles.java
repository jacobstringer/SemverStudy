package auxiliary_CountSubModules;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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

import dataacquisition_GithubScraper.Counter;
import dataacquisition_GithubScraper.ProductionMBean;

public class ProductionParseFiles implements ProductionMBean {
	private static final int PRODUCER_COUNT = 1;
	private static final int CONSUMER_COUNT = 10;
	private static final int BUFFER_SIZE = 1000;

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
	private List<Connection> connections = new ArrayList<Connection>();
	private BlockingQueue <String[]> queue = new ArrayBlockingQueue<String[]>(BUFFER_SIZE);
	private Counter jobCounter = new Counter("processed jobs");

	public static void main(String[] args) {
		new ProductionParseFiles().start();
	}

	public void start() {

		// DB
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e2) {
			e2.printStackTrace();
		}
		
		// start producers
		for (int i=0;i<PRODUCER_COUNT;i++) {
			ProducerParseFiles producer = new ProducerParseFiles(queue, CONSUMER_COUNT);
			Thread thread = new Thread(producer);
			thread.setName("producer - " + (1)); // name to facilitate analysis with VisualVM or similar tool
			thread.start();
			producers.add(producer);
		}

		// start consumers
		for (int i=0;i<CONSUMER_COUNT;i++) {
			ConsumerParseFiles consumer = null;
			try {
				connections.add(DriverManager.getConnection(
						"jdbc:postgresql://localhost:5432/BuildData", "postgres", "password"));
				consumer = new ConsumerParseFiles(queue, connections.get(connections.size()-1));
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