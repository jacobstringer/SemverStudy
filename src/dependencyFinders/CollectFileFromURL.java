package dependencyFinders;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CollectFileFromURL {
	
	private static String[] tokens = null;
	
	private static long[] timeout = new long[tokens.length];
	private static int current = 0;

	private static OkHttpClient client = new OkHttpClient();
	
	private static void readTokens() {
		ArrayList<String> templist = new ArrayList<>();
		try(BufferedReader in = new BufferedReader(new FileReader(new File("src/accesstokens.csv")))) {
			String temp;
			while ((temp = in.readLine()) != null) {
				templist.add(temp);
			}
			tokens = new String[templist.size()];
			for (int i = 0; i < tokens.length; i++) {
				tokens[i] = "access_token=" + templist.get(i).split(",")[0];
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String urlGithubPlainText (String url) {
		String[] temp = url.replaceAll("github.com", "raw.githubusercontent.com").split("/");
		temp[temp.length-1] = "master/" + temp[temp.length-1];
		StringBuilder temp2 = new StringBuilder();
		for (String i: temp) {
			temp2.append(i);
			temp2.append("/");
		}
		String temp3 = temp2.toString();
		return temp3.substring(0, temp3.length()-1);
	}
	
	private static synchronized void tokenCheck() {
		if (tokens == null) {
			readTokens();
		}
	}
	
	public static String getFile(String url) throws IllegalArgumentException {
		tokenCheck();
		
		while(true) {
			Request request = new Request.Builder().url(url+"?"+tokens[current]).build();
			try (Response response = client.newCall(request).execute()) {
				if (response.code() == 200) {
					return response.body().string();
				}
				else {
					return "";
				}
			} catch (UnknownHostException e) {
				// Will wait for an hour (and a second) after timeout before trying this token again
				timeout[current] = System.currentTimeMillis() + 60*60*1000 + 1000;
				int next = (current + 1) % tokens.length;
				
				try {
					System.out.println("Sleeping for " + Long.toString(timeout[next] - System.currentTimeMillis()) + " before trying next token");
					Thread.sleep(timeout[next] - System.currentTimeMillis());
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				} catch (IllegalArgumentException e2) {}
				
				current = next;

			} catch (Exception e) {
				return null;
			}
		}

	}
}
