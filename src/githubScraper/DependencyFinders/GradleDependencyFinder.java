package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;

	public GradleDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	Pattern p = Pattern.compile("dependencies\\s*\\{[^}]+\\}");	
	Pattern version = Pattern.compile("[:\'\"]\\d+(\\.\\d+){0,2}");	
	
	private void printString(String s) {
		try {
			out.write(s);
			out.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void findVersionData(String file, String url) {
		/*
		Matcher m = p.matcher(file);
		String deps = "";
		
		if (m.find()) {
			deps = m.group();
			printString(deps);
		} else { // no dependencies
			return; 
		}

		deps = deps.replaceAll("(dependencies|\\{|\\}+)", "").trim();
		for (String dep: deps.split("\n")) {
			dep = dep.trim();
			Matcher mv = version.matcher(dep);
			
			if (mv.find()) {
				printString(mv.group().replaceAll("[:\'\"]", ""));
			}
			
			if (dep.contains("compile")){}
		}
		*/
		
		// Starts after the first dependency {
		int dep_index = file.indexOf("dependencies");
		if (dep_index == -1) {return;}
		int index = file.indexOf("{", dep_index) + 1;
		int open_bracket = 1;
		int close_bracket = 0;
		int start_index = index;
		
		// Finds the entire dependencies closure and allows for nested closures
		while (open_bracket > close_bracket) {
			if (file.charAt(index) == '{') {
				open_bracket++;
			} else if (file.charAt(index) =='}') {
				close_bracket++;
			}
			index++;
		}
		
		//String deps = file.substring(start_index, index-1);
		String deps = file.substring(dep_index, index);
		//printString(file);
		printString(deps);
	}

}
