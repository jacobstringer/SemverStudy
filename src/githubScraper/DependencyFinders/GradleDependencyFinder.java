package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;
	//int counter = 0;

	public GradleDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	Pattern totalVersion = Pattern.compile("(\'|\").+(\'|\")");	
	Pattern numberVersion = Pattern.compile("\\d+(\\.[\\+\\d]+){0,2}");
	Pattern variableVersion = Pattern.compile("\\$[^\'\":]+");

	private void printString(String s) {
		try {
			out.write(s);
			out.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Find closure nearest to index, used for once the dependencies keyword is found
	private String getClosure(String file, int index) {
		int bracket_level = 0;		

		try {
			// Fast forward to first {
			while (Character.isWhitespace(file.charAt(index))) {index++;}
			if (file.charAt(index) != '{') {
				return "";
			} else {
				index++;
				bracket_level++;
			}
			int initial_index = index;

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

	// Find dependency closure at level 0, pass in entire file
	private String getBaseDependenciesClosure(String file) {
		int bracket_level = 0;	
		int index = 0;

		try {
			while (true) {
				if (file.charAt(index) == '{') {
					bracket_level++;
				} else if (file.charAt(index) =='}') {
					bracket_level--;
				} else if (bracket_level == 0 && file.regionMatches(index, "dependencies", 0, "dependencies".length())) {
					String temp = getClosure(file, index + "dependencies".length());
					// If this dependencies is not the dependencies closure (e.g. it is a comment or import), continue searching
					if (!temp.equals("")) {
						return temp;
					}
				}
				index++;
			}
		} catch (IndexOutOfBoundsException e) { // Caught at EOF
			return "";
		}
	}
	
	// Takes one line of dependencies, resolves any variables, and returns the version number
	private String getVersionNum (String line, String file, String url) {
		String version = "";
		Matcher m = totalVersion.matcher(line);
		if (m.find()) {
			// total is everything inside brackets on the line
			String total = m.group();
			try {
				// Captures number versions
				m = numberVersion.matcher(total.split(":")[2]);
				if (m.find()) {
					return m.group();
				} 
				
				// Captures variable versions
				m = variableVersion.matcher(total.split(":")[2]);
				if (m.find()) { 
					String variable = m.group().replaceAll("[\\$\\{\\}]", "");
					Pattern pvar = Pattern.compile("(?:" + variable + "\\s*=\\s*[\'\"])[^\'\"]+(?:[\'\"])");
					m = pvar.matcher(file);
					if (m.find()) {
						Matcher m2 = numberVersion.matcher(m.group());
						if (m2.find()) {
							return m2.group();
						}
					}
					printString("No variable found for: " + variable + " in file: " + url);
				}
			} catch (IndexOutOfBoundsException e) { // Catches non-normal amount of colons
				//System.err.println(line);
			}
		}
		
		return version;
	}

	@Override
	public void findVersionData(String file, String url) {
		// Get dependencies closure		
		String deps = getBaseDependenciesClosure(file);
		if (deps == "") {
			return;
		}

		// Print info
		//printString(file);
		printString(deps);
		//System.out.println(counter++);
		
		// Some commands can be used over multiple lines
		String last_command = "";
		
		// Check dependencies lines one by one
		for (String line: deps.split("\n")) {
			// Filter only compile and testCompile tasks
			String type = "";
			line = line.trim();
			if (line.startsWith("compile")) {
				last_command = "compile";
			} else if (line.startsWith("testCompile")) {
				last_command = "testCompile";
			} else if (Pattern.matches("^[\'\"]", line)) {
				// last_command is now this command
			} else {
				continue;
			}

			// Catches multiline statements (closures)
			//if (Pattern.matches(type + "\\s*(", line)) {
				//line.
			//}

			// Extracts version information out of line
			String version = getVersionNum(line, file, url);
			printString(last_command + ": " + version);
		}
	}

}
