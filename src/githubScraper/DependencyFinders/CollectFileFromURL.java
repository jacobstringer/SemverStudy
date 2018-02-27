package githubScraper.DependencyFinders;

import java.io.IOException;
import java.net.UnknownHostException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CollectFileFromURL {

	private static OkHttpClient client = new OkHttpClient();
	
	public static String getFile(String url) throws IllegalArgumentException {
		Request request = new Request.Builder()
				.url(url)
				.build();
		while(true) {
			try (Response response = client.newCall(request).execute()) {
				if (response.code() == 200) {
					return response.body().string();
				}
				else {
					return "";
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
}
