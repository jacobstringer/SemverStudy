package githubScraper.DependencyFinders;

import java.sql.Connection;

public interface DependencyFinder {
	public void findVersionData(String file, String url);
}
