package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Able to be used in concurrent mode due to additional HTTP calls for extra files
public class GradleDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;

	public GradleDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	// Global patterns to speed up performance - they are for gathering version data out of the file
	public static final Pattern TOTAL_VERSION = Pattern.compile("(\'|\")[^\'\"]+(\'|\")");
	public static final Pattern INCLUDE_CONCATENATED_VERSIONS = Pattern.compile("(\'|\")[^\'\"]+(\'|\")(\\s*\\+\\s*\\w+){1,}");
	public static final Pattern MAP_VERSION_PATTERN = Pattern.compile("version:\\s*[\'\"]?[^\'\"]+[\'\"]?");
	public static final Pattern NUMBER_VERSION = Pattern.compile("(\\d+|\\+)(\\.[\\+\\d]+){0,2}");
	public static final Pattern VARIABLE_VERSION = Pattern.compile("\\$[\\{a-zA-Z_][^\'\":]+");
	public static final Pattern FIND_COMMAND = Pattern.compile("^\\w+");
	public static final Pattern VARIABLES = Pattern.compile("^[a-zA-Z]+\\s+\\w+(,\\s*\\w+){0,}\\s*$");
	public static final Pattern RANGE = Pattern.compile("[\\[\\(\\]\\)][^\\[\\(\\]\\)]+[\\[\\(\\]\\)]");

	// Patterns for sorting the style of version
	public static final Pattern MAJOR = Pattern.compile("latest|^[\'\"]?\\d*\\+");
	public static final Pattern MINOR = Pattern.compile("^[\'\"]?\\d+\\.\\d*\\+");
	public static final Pattern MICRO = Pattern.compile("^[\'\"]?\\d+\\.\\d+\\.\\d*\\+");
	public static final Pattern FIXED = Pattern.compile("^[\'\"]?\\d+(\\.\\d+){0,2}");


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
		if (file == null || varname == null)
			return variable;
		
		try {
			Pattern getVariableArray = Pattern.compile(varname + "\\s*=\\s*\\[[^\\[\\]]*\\]");
			Pattern getVariableQuotes = Pattern.compile(varname + "\\s*=\\s*[\"\'][^\"\']+[\"\']");
			Matcher m = getVariableArray.matcher(file);
			Matcher m2 = getVariableQuotes.matcher(file);
	
			if (m.find()) {
				variable = Arrays.asList(m.group().replaceAll(varname+"\\s*=\\s*\\[", "").replaceAll("(]\"\')", "").split(",\n"));
			} else if (m2.find()) {
				variable.add(m2.group().replaceAll(varname+"\\s*=\\s*", "").replaceAll("[\"\']", ""));
			}
		} catch (PatternSyntaxException e) {
			// Occasionally comes up for variables stored in maps, e.g. version: versions['hibernate-core']
			// Quite difficult to avoid with regex, add to threats to validity
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
					ext.add(CollectFileFromURL.getFile(url.replaceAll("github", "raw.githubusercontent") + "/master/" + temp 
							+ "?access_token=70580404d854ec52e85f6f93675e792eac2faccb"));
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
		Matcher mcat = INCLUDE_CONCATENATED_VERSIONS.matcher(line);

		try {
			// Get variables and versions for further processing
			// Version is written as a map
			if (mmap.find()) {
				rawVersions.add(mmap.group());
				// Version uses concatenation
			} else if (mcat.find()) {
				String[] t = mcat.group().split("\\+");
				rawVersions.add("$"+t[t.length-1].trim());
				// Version is written as a string
			} else if (m.find()) { 
				try {
					rawVersions.add(m.group().split(":")[2]);
					while(m.find()) {
						rawVersions.add(m.group().split(":")[2]);
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					// Ignore these, picks up the occasional version but false positive rate far too high
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
						//System.out.println(temp[i]);
						//System.out.println(url);
					}

				}
				// Neither, empty list returned
			} else { 
				return resolvedVersions;
			}

			// Process raw variables and versions
			// For loop: in case there is more than one dependency on the same line
			for (String tot: rawVersions) {
				// Captures ranges
				m = RANGE.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				}

				// Captures number versions
				m = NUMBER_VERSION.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				} 

				// Resolves variables into versions i.e. ${springVersion}
				m = VARIABLE_VERSION.matcher(tot);
				if (m.find()) { 
					resolvedVersions.addAll(getVersionFromVariable(file, ext, m.group(), url));
					//continue;
				}

				// Captures ivy style 'latest'
				m = MAJOR.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				}
			}
		} catch (IndexOutOfBoundsException e) { // Triggers when group:project:version syntax is not followed
			//printString("\t\t"+line);
			//resolvedVersions.add("noVersion "+line); // Once commented out, no version lines will return an empty list - excludes trip this often
		} catch (Exception e) {
			e.printStackTrace();
		}

		return resolvedVersions;
	}

	private String processVersion(String version) {
		Matcher m = RANGE.matcher(version);
		if (m.find())
			return "range";

		m = MICRO.matcher(version);
		if (m.find())
			return "micro";

		m = MINOR.matcher(version);
		if (m.find())
			return "minor";

		m = MAJOR.matcher(version);
		if (m.find())
			return "major";

		m = FIXED.matcher(version);
		if (m.find())
			return "fixed";

		return null;
	}

	// ENTRY POINT INTO CLASS. PROVIDE ENTIRE GRADLE FILE AS A STRING ALONG WITH URL FOR RECORDING
	@Override
	public void findVersionData(String file, String url) {
		// Get dependencies closure		
		String deps = getBaseDependenciesClosure(file);
		if (deps == "") {
			return;
		}

		// Counts per file
		int files = 0;
		int methods = 0;
		int fixedVersions = 0;
		int microVersions = 0;
		int minorVersions = 0;
		int majorVersions = 0;	
		int rangeVersions = 0;
		int deplines = deps.split("\n").length;
		ArrayList<String[]> individualEntries = new ArrayList<>();

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

			// Continue if the line does not have any further information after the command
			if (Pattern.matches(lastCommand + "\\s*\\(?\\s*$", line)) {
				continue;
			}


			if (Pattern.matches(lastCommand + "\\s+file(s|Tree).*", line)) {
				// Counts local file dependencies
				files++;
			} else if (Pattern.matches(lastCommand+"\\s+\\w+\\(\\).*", line)) {
				// Counts method calls
				methods++;
			} else {
				// Extracts version information out of line
				// When no version information is found, empty list is returned
				List<String> version = getVersionNum(line, file, url);
				for (String v: version) {
					// Figure out what the version is, skip if it is not usable
					String result = processVersion(v);
					if (result == null) {
						continue;
					}

					// Tally
					switch(result) {
					case "fixed": {fixedVersions++; break;}
					case "micro": {microVersions++; break;}
					case "minor": {minorVersions++; break;}
					case "major": {majorVersions++; break;}
					case "range": {rangeVersions++; break;}
					}

					// Save for later DB entry
					individualEntries.add(new String[]{lastCommand, result, v});
				}
			}
		}

		// Add to database, first to gradlefiles, then the individual results to gradleentries
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("INSERT into gradlefiles (url, fixed, micro, minor, major, nrange, "
					+ "lines, files, methods) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
			ps.setString(1, url);
			ps.setInt(2, fixedVersions);
			ps.setInt(3, microVersions);
			ps.setInt(4, minorVersions);
			ps.setInt(5, majorVersions);
			ps.setInt(6, rangeVersions);
			ps.setInt(7, deplines);
			ps.setInt(8, files);
			ps.setInt(9, methods);
			ps.execute();

			for (String[] entry: individualEntries) {
				ps = c.prepareStatement("INSERT into gradleentries (url, command, versiontype, raw) "
						+ "VALUES (?, ?, ?, ?)");
				ps.setString(1, url);
				ps.setString(2, entry[0]);
				ps.setString(3, entry[1]);
				ps.setString(4, entry[2]);
				ps.execute();
			}
		} catch (SQLException e) {
			//System.err.println(e.getMessage());
		}
	}

}
