package googleScraper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gs {
	
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
		Gs example = new Gs();
		List<String> info;
		BufferedWriter out = new BufferedWriter(new FileWriter(new File("urls200.txt")));
		BufferedReader in = new BufferedReader(new FileReader(new File("fromgoogle.txt")));
		
		String response = in.readLine();
		while(response != null) {
			info = example.mysplit(response);
			for (String temp: info) {
				out.write(temp);
				out.write("\n");
				System.out.println(temp);
			}
			response = in.readLine();
		}
		out.close();
	}
}

