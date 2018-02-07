package githubScraper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FileSaver {

	private static OkHttpClient client = new OkHttpClient();
	private static String[] scripts = {
			"pom.xml",
			"build.xml",
			"package.json",
			"Rakefile",
			"build.gradle"
	};

	private static String run(String url) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.build();
		while(true) {
			try (Response response = client.newCall(request).execute()) {
				if (response.code() == 200) {
					return response.body().string();
				}
				else {
					return null;
				}
			} catch (UnknownHostException e) {
				System.out.println(e.getMessage());
				System.out.println("Will try again in a minute");
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			} catch (Exception e) {
				return null;
			}
		}

	}

	public static String[] findSaveFile(String url) throws IOException {
		String file = "";

		for (String build: scripts) {
			file = run(url.replace("github", "raw.githubusercontent") + "/master/" + build);
			if (file != null) {
				String folder = "githubSamples/";
				switch(build) {
				case "pom.xml": {
					folder += "Pom/";
					break;
				}
				case "build.xml": {
					folder += "Build/";
					break;
				}
				case "package.json": {
					folder += "Package/";
					break;
				}
				case "Rakefile": { 
					folder += "Rake/";
					break;
				}
				case "build.gradle": {
					folder += "Gradle/";
					break;
				}
				}

				BufferedWriter out = new BufferedWriter(new FileWriter(
						new File((folder+(url.replace("https://github.com/", "").replace("/", "+"))+"+"+build))));
				out.write(file);
				out.close();
				String[] temp = new String[2];
				temp[0] = build;
				temp[1] = file;
				return temp;
			}
		}

		return null;
	}
}
