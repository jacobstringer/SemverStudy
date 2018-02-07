package githubScraper;

public interface ProductionMBean {
	public int getBufferSize();
	public int getMaxBufferSize();
	public int getProcessedJobCount();
	public void stop();
}
