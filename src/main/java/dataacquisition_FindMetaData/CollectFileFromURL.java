package dataacquisition_FindMetaData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class CollectFileFromURL {

	private static String[] tokens;
	static {
		System.out.println("Initialising tokens");
		readTokens();
	}

	private static long[] timeout = new long[tokens.length];
	private static int requests = 0;
	private static int current = 0;
	private static boolean needToUpdate = false;
	private static int forUpdate = 0;
	private static long last = System.currentTimeMillis();

	private static OkHttpClient client = new OkHttpClient();

	// Update to adjust base url for different information
	private static String urlGithubPlainText (String url) {
		return url.replaceAll("github.com", "api.github.com/repos") + "/commits";
	}

	// Updates the token. If the updated token is not ready to be used again yet, it sleeps the programme until it is.
	private static synchronized void incrementCurrent() {
		timeout[current] = System.currentTimeMillis() + 15*60*1000 + 1000;
		current = (current + 1) % tokens.length;
		System.out.println("Current token is now: " + current);
		
		if (timeout[current] - System.currentTimeMillis() > 0) {
			try {
				System.out.println("Sleeping programme for " +((timeout[current] - System.currentTimeMillis())/60000)+" minutes");
				Thread.sleep(timeout[current] - System.currentTimeMillis());
			} catch (InterruptedException e) {}
		}
	}
	
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
		for (String i: tokens)
			System.out.println(i);
	}

	// Controls access to tokens and provides a counter so it can be updated when near the hourly limit
	private static synchronized int getToken() {
		if (requests++ > 1200) {
			requests = 0;
			incrementCurrent();
		} else if (needToUpdate) {
			needToUpdate = false;
			if (current == forUpdate) {
				requests = 0;
				incrementCurrent();
			}
		}
		
		// Stagger requests so they are only sent every 20ms at the minimum
		if (last + 20 > System.currentTimeMillis()) {
			try {
				Thread.sleep(last + 20 - System.currentTimeMillis());
			} catch (InterruptedException e) {}
		}
		
		last = System.currentTimeMillis();

		return current;
	}

	// Collect from url github data. Uses access tokens, with protected access through synchronized methods
	public static String getFile(String url) throws IllegalArgumentException {

		while(true) {
			int token = getToken();

			Request request = new Request.Builder().url(urlGithubPlainText(url)+"?"+tokens[token]).build();
			try {
				Response response = client.newCall(request).execute();
				if (response.code() == 200) {
					return response.body().string();
				} else if (response.code() == 403) {
					System.out.println("Token " + current + " was not useable after " + requests + " requests");
					needToUpdate = true;
					forUpdate = token;
				} else {
					return "";
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
}
