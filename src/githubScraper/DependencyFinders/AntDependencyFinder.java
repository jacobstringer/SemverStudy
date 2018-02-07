package githubScraper.DependencyFinders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntDependencyFinder implements DependencyFinder {

	@Override
	public int[] findVersionData(String file) {
		// Return int[]{Major, Minor, Micro, Semantic, Non-Semantic, Additional Build Files, Environment Variables}
		int[] info = new int[7];
		Pattern p = Pattern.compile("regex goes here");
		Matcher m = p.matcher(file);
		String temp = null;
		while (true) {
			if (m.find()) {
				temp = m.group();
				
			} else {
				break;
			}
		}
		return info;
	}

}
