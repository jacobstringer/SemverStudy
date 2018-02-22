package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GradleDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;
	
	HashMap<String, Integer> commands = new HashMap<String, Integer>();
	String[] wantedCommands = new String[]{"compile", "testCompile", "runtime", "testRuntime"};

	public GradleDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	Pattern totalVersion = Pattern.compile("(\'|\")[^\'\"]+(\'|\")");
	Pattern mapVersionPattern = Pattern.compile("version:\\s*[\'\"][^\'\"]+[\'\"]");
	Pattern numberVersion = Pattern.compile("\\d+(\\.[\\+\\d]+){0,2}");
	Pattern variableVersion = Pattern.compile("\\$[^\'\":]+");
	Pattern findCommand = Pattern.compile("^[a-zA-Z]+");

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
		String total = "";
		Matcher mmap = mapVersionPattern.matcher(line);
		Matcher m = totalVersion.matcher(line);

		try {
			if (mmap.find()) {
				// total is everything inside brackets on the line
				total = mmap.group();
			} else if (m.find()) {
				total = m.group().split(":")[2];
			} else {
				return version;
			}

			// Captures number versions
			m = numberVersion.matcher(total);
			if (m.find()) {
				return m.group();
			} 

			// Captures variable versions
			m = variableVersion.matcher(total);
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
		printString(deps);

		// Some commands can be used over multiple lines
		String last_command = "";

		// Check dependencies lines one by one
		for (String line: deps.split("\n")) {
			line = line.trim();
			String type = "";
			
			// Find command
			Matcher m = findCommand.matcher(line);
			if (m.find()) {
				type = m.group();
			}
			
			// Accounts for multi-line commands
			if (!Pattern.matches("^[\'\"\\[].+", line)) {
				last_command = type;
			}
			
			// Counts types of commands
			Integer i = commands.get(last_command);
			if (i == null)
				commands.put(last_command, 1);
			else	
				commands.put(last_command, ++i);
			
			// Ignores commands that are not useful for analysis
			if (!Arrays.asList(wantedCommands).contains(last_command))
				continue;

			// Continue if the line does not have any further information
			if (Pattern.matches(last_command + "\\s*\\(?\\s*$", line)) {
				continue;
			}

			// Extracts version information out of line
			String version = getVersionNum(line, file, url);
			printString(last_command + ": " + version);
		}
	}

}
