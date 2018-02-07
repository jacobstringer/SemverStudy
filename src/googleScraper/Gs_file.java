package googleScraper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Gs_file {
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
	
	public List<String> mysplit(String text) {
		List<String> s = new ArrayList<String>();
		String regex = "https://github.com/(.)*pom.xml";
		Matcher m = Pattern.compile(regex).matcher(text);
		while(m.find()) {
			s.add(m.group());
		}
		return s;
	}

	public static void main(String[] args) throws IOException {
		Gs_file example = new Gs_file();
		String response;
		List<String> info;
		BufferedWriter out = new BufferedWriter(new FileWriter(new File("urls200.txt")));
		
		for(int i = 200; i < 1000; i += 10) {
			response = example.run("https://www.google.co.nz/search?q=site:github.com+inurl:pom.xml&rlz=1C1CHBF_enNZ702NZ702&ei=4M0lWr6LD8H98QWTm7foBQ&start="+i+"&sa=N&biw=1280&bih=615");
			System.out.println(response);
			info = example.mysplit(response);
			
			if (info.isEmpty()) {
				System.out.println(i);
				break;
			}
			
			for (String temp: info) {
				temp = temp.split("&amp;")[0];
				out.write(temp);
				out.write("\n");
				System.out.println(temp);
			}
			
			try {
				Thread.sleep(500);
			} catch (Exception e) {}
		}
		out.close();
	}
}

