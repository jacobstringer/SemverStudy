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

	// Find closure nearest to index
	private String getClosure(String file, int index) {
		int bracket_level = 1;		

		try {
			// Fast forward to first {
			int initial_index = index;
			while (file.charAt(index) != '{') {index++;}
			//int initial_index = index;

			// Finds the entire dependencies closure and allows for nested closures
			while (bracket_level > 0) {
				if (file.charAt(index) == '{') {
					bracket_level++;
				} else if (file.charAt(index) =='}') {
					bracket_level--;
				}
				index++;
			}
			return file.substring(initial_index, index - 1);
		} catch (IndexOutOfBoundsException e) {
			return "";
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

		// Finds where the dependencies closure is
		int dep_index = file.indexOf("dependencies");
		if (dep_index == -1) {
			return;
		}

		// Get closure		
		String deps = getClosure(file, dep_index);
		
		// Print info
		//printString(file);
		printString(deps);
		
		/*
		// Line count
		int lineNo = 0;

		// Print out Gradle dependency commands
		for (String line: deps.split("\n")) {
			// Filter only compile and testCompile tasks
			String type = "";
			if (line.trim().equals("compile")) {
				type = "compile";
			} else if (line.trim().equals("testCompile")) {
				type = "testCompile";
			} else {
				continue;
			}
			
			// Catches multiline statements (closures)
			if (Pattern.matches(type + "\\s*(", line)) {
				line.
			}
			
			//Matcher m = pg.matcher(line);
			//if (m.find()) {
			//	printString(m.group());
			//}
			lineNo++;
		}
		*/
	}

}
