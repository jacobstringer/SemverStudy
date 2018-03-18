package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Able to be used in concurrent mode due to additional HTTP calls for extra files
public class GradleDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;
	
	int found = 0;
	int files = 0;
	int methods = 0;
	int noVersion = 0;
	public HashMap<String, Integer> commands = new HashMap<String, Integer>();
	public HashMap<String, Integer> notFound = new HashMap<String, Integer>();
	public static final String[] WANTED_COMMANDS = new String[]{
			"compile", "testCompile", 
			"runtime", "testRuntime",
			"provided", "providedCompile",
			"compileOnly", "compilerCompile",
	};
	public static final String[] COMMANDS_TO_PRINT = new String[] {
			"provided", "providedCompile",
			"compileOnly", "compilerCompile",
	};

	public GradleDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	public static final Pattern TOTAL_VERSION = Pattern.compile("(\'|\")[^\'\"]+(\'|\")");
	public static final Pattern MAP_VERSION_PATTERN = Pattern.compile("version:\\s*[\'\"][^\'\"]+[\'\"]");
	public static final Pattern NUMBER_VERSION = Pattern.compile("\\d+(\\.[\\+\\d]+){0,2}");
	public static final Pattern VARIABLE_VERSION = Pattern.compile("\\$[^\'\":]+");
	public static final Pattern FIND_COMMAND = Pattern.compile("^[a-zA-Z]+");
	public static final Pattern LATEST = Pattern.compile("latest");
	public static final Pattern VARIABLES = Pattern.compile("^[a-zA-Z]+\\s+\\w+(,\\s*\\w+){0,}\\s*$");

	private synchronized void incrementFound() {
		found++;
	}
	private synchronized void incrementFiles() {
		files++;
	}
	private synchronized void incrementMethods() {
		methods++;
	}
	private synchronized void incrementNoVersion() {
		noVersion++;
	}
	private synchronized void addCommands(String command) {
		Integer i = commands.get(command);
		if (i == null)
			commands.put(command, 1);
		else	
			commands.put(command, ++i);
	}
	private synchronized void addNotFound(String variable) {
		Integer i = notFound.get(variable);
		if (i == null)
			notFound.put(variable, 1);
		else	
			notFound.put(variable, ++i);
	}
	
	private synchronized void printString(String s) {
		try {
			out.write(s);
			out.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Find closure nearest to index, used for once the dependencies keyword is found
	private String getNextClosure(String file, int index) {
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
					String temp = getNextClosure(file, index + "dependencies".length());
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

	// Use if there is a variable listed in the command - fetches variable(s)
	private List<String> getVariable (String file, String varname) {
		List<String> variable = new ArrayList<>();

		Pattern getVariableArray = Pattern.compile(varname + "\\s*=\\s*\\[[^\\[\\]]*\\]", Pattern.DOTALL);
		Pattern getVariableQuotes = Pattern.compile(varname + "\\s*=\\s*[\"\'][^\"\']+[\"\']");
		Matcher m = getVariableArray.matcher(file);
		Matcher m2 = getVariableQuotes.matcher(file);

		if (m.find()) {
			variable = Arrays.asList(m.group().replaceAll(varname+"\\s*=\\s*\\[", "").replaceAll("]\"\'", "").split(",\n"));
		} else if (m2.find()) {
			variable.add(m2.group().replaceAll(varname+"\\s*=\\s*", "").replaceAll("[\"\']", ""));
		}

		return variable;
	}

	//
	private List<String> getVersionFromVariable (String file, List<String> ext, String var, String url) {
		String variable = var.replaceAll("[\\$\\{\\}]", "");
		List<String> temp = getVariable(file, variable);
		if (!temp.isEmpty()) {
			return temp;
		}

		// Looks for external files if the variables are not in this file
		if (ext.isEmpty()) {
			Pattern p = Pattern.compile("apply from:.*");
			Matcher matcher = p.matcher(file);
			while (matcher.find()) {
				temp.add(matcher.group().replaceAll("apply from:\\s*", "").replaceAll("[\'\"]", "").trim());
				// Sometimes apply from: use a relative path, sometimes a full url, so try both ways
				try {
					ext.add(CollectFileFromURL.getFile(temp.get(temp.size()-1)));
				} catch (IllegalArgumentException e) {
					ext.add(CollectFileFromURL.getFile(url.replaceAll("github", "raw.githubusercontent") + "/master/" + temp));
				}
			}
		}

		// Checks for variables in external files
		for (String s: ext) {
			temp.addAll(getVariable(s, variable));
			if (!temp.isEmpty()) {
				return temp;
			}
		}

		// Records unmatched variables
		addNotFound(variable);
		
		return temp;
	}

	// Takes one line of dependencies (trimmed), resolves any variables, and returns the version number
	private List<String> getVersionNum (String line, String file, String url) {
		// Returns empty list when version number cannot be found
		List<String> resolvedVersions = new ArrayList<>();
		List<String> rawVersions = new ArrayList<>();
		List<String> ext = new ArrayList<>();
		Matcher mmap = MAP_VERSION_PATTERN.matcher(line);
		Matcher m = TOTAL_VERSION.matcher(line);
		Matcher mvariable = VARIABLES.matcher(line);

		try {
			// Get variables and versions for further processing
			// Version is written as a map
			if (mmap.find()) {
				rawVersions.add(mmap.group());
			// Version is written as a string
			} else if (m.find()) { 
				rawVersions.add(m.group().split(":")[2]);
				while(m.find()) {
					rawVersions.add(m.group().split(":")[2]);
				}
			// Line lists variables which need resolving
			} else if (mvariable.find()) { 
				String[] temp = mvariable.group().split("[,\\s]+");
				for (int i=1; i < temp.length; i++) {
					List<String> temp2 = getVariable(file, temp[i]);
					if (!temp2.isEmpty()) {
						for (int j=0; j < temp2.size(); j++) {
							resolvedVersions.addAll(getVersionNum(temp2.get(j), file, url));
						}
					} else { // No variable found... print variable and url to system out
						System.out.println(temp[i]);
						System.out.println(url);
					}

				}
			// Neither, empty list returned
			} else { 
				return resolvedVersions;
			}
			
			// Process raw variables and versions
			// For loop: in case there is more than one dependency on the same line
			for (String tot: rawVersions) {
				// Resolves variables into versions i.e. ${springVersion}
				m = VARIABLE_VERSION.matcher(tot);
				if (m.find()) { 
					resolvedVersions.addAll(getVersionFromVariable(file, ext, m.group(), url));
					continue;
				}

				// Captures number versions
				m = NUMBER_VERSION.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				} 

				// Captures ivy style 'latest'
				m = LATEST.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				}
			}
		} catch (IndexOutOfBoundsException e) { // Triggers when group:project:version syntax is not followed
			//printString("\t\t"+line);
			incrementNoVersion();
			resolvedVersions.add("noVersion "+line); // Once commented out, no version lines will return an empty list
		} catch (Exception e) {
			e.printStackTrace();
		}

		return resolvedVersions;
	}

	// ENTRY POINT INTO CLASS. PROVIDE ENTIRE GRADLE FILE AS A STRING ALONG WITH URL FOR RECORDING
	@Override
	public void findVersionData(String file, String url) {
		// Get dependencies closure		
		String deps = getBaseDependenciesClosure(file);
		if (deps == "") {
			return;
		}

		// Some commands can be used over multiple lines
		String lastCommand = "";

		// Check dependencies lines one by one
		for (String line: deps.split("\n")) {
			line = line.trim();
			String type = "";

			// Find command
			Matcher m = FIND_COMMAND.matcher(line);
			if (m.find()) {
				type = m.group();
			}

			// Accounts for multi-line commands
			if (!Pattern.matches("^[\'\"\\[].+", line)) {
				if (type.equals("")) { // Filters out comment lines and configuration lines
					continue;
				}
				lastCommand = type;
			}

			// Counts types of commands
			addCommands(lastCommand);

			/*
			// Ignores commands that are not useful for analysis
			if (!Arrays.asList(WANTED_COMMANDS).contains(lastCommand)) {
				printString(line);
				continue;
			}*/
				

			// Continue if the line does not have any further information after the command
			if (Pattern.matches(lastCommand + "\\s*\\(?\\s*$", line)) {
				continue;
			}

			// Counts local file dependencies
			if (Pattern.matches(lastCommand + "\\s+file(s|Tree).*", line)) {
				incrementFiles();
				continue;
			}

			// Counts method calls to resolve dependencies
			if (Pattern.matches(lastCommand+"\\s+\\w+\\(\\).*", line)) {
				incrementMethods();
				continue;
			}

			// Extracts version information out of line
			// When no version information is found, empty list is returned
			List<String> version = getVersionNum(line, file, url);
			printString(line);
			printString(version.toString() + " " + lastCommand);
			incrementFound();

			/*if (version.isEmpty()) { // No information found for this version
				Pattern p = Pattern.compile("apply from:.*");
				Matcher matcher = p.matcher(file);
				while (matcher.find()) {
					printString(matcher.group());
				}
				printString(url);
				printString(lastCommand + ": " + line);
			}*/
		}
	}

}
