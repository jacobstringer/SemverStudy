package pom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class POMScraper {
	OkHttpClient client = new OkHttpClient();

	public String run(String url) throws IOException {
		Request request = new Request.Builder()
				.url(url)
				.build();

		try (Response response = client.newCall(request).execute()) {
			return response.body().string();
		} catch (Exception e) {
			System.err.println(e);
			return null;
		}
	}
	
	public static void main(String[] args) throws Exception {
		BufferedReader in = new BufferedReader(new FileReader(new File("urls.txt")));
		String url;
		POMScraper col = new POMScraper();
		
		while(true) {
			url = in.readLine();
			if (url == null) break;
			url = String.join("", url.split("/blob"));
			url = url.replaceFirst("github", "raw.githubusercontent");
			System.out.println(url);
			
			BufferedWriter out = new BufferedWriter(new FileWriter(new File("samples/"+url.replace("https://raw.githubusercontent.com/", "").replace("/", "+")))); //
			out.write(col.run(url));
			out.close();
		}
		in.close();
		
	}

}
