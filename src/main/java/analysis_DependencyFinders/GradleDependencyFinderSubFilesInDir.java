package analysis_DependencyFinders;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
public class GradleDependencyFinderSubFilesInDir implements DependencyFinder {
	Connection c;
	Writer out;

	public GradleDependencyFinderSubFilesInDir(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	// Global patterns to speed up performance - they are for gathering version data out of the file
	private static final Pattern TOTAL_VERSION = Pattern.compile("(\'|\")[^\'\"]+(\'|\")");
	private static final Pattern INCLUDE_CONCATENATED_VERSIONS = Pattern.compile("(\'|\")[^\'\"]+(\'|\")(\\s*\\+\\s*\\w+){1,}");
	private static final Pattern MAP_VERSION_PATTERN = Pattern.compile("version:\\s*[\'\"]?[^\'\"]+[\'\"]?");
	private static final Pattern NUMBER_VERSION = Pattern.compile("[\\d\\+]+(\\.[\\+\\d]+){0,2}");
	private static final Pattern VARIABLE_VERSION = Pattern.compile("\\$?[\\{a-zA-Z_]?[^\'\":]+");
	private static final Pattern FIND_COMMAND = Pattern.compile("^\\w+");
	private static final Pattern VARIABLES = Pattern.compile("^[a-zA-Z]+\\s+\\w+(,\\s*\\w+){0,}\\s*$");
	private static final Pattern RANGE = Pattern.compile("[\\[\\(\\]\\)][^\\[\\(\\]\\)a-zA-Z]+[\\[\\(\\]\\)]");
	private static final Pattern PNUMBER = Pattern.compile("\\d+(\\.\\d+){0,2}");

	// Patterns for sorting the style of version
	private static final Pattern MAJOR = Pattern.compile("(latest|^[\'\"]?\\d*\\+)");
	private static final Pattern MINOR = Pattern.compile("^[\'\"]?\\d+\\.\\d*\\+");
	private static final Pattern MICRO = Pattern.compile("^[\'\"]?\\d+\\.\\d+\\.\\d*\\+");
	private static final Pattern FIXED = Pattern.compile("^[\'\"]?\\d+(\\.\\d+){0,2}");

	// Diagnostics
	public int unfoundVariables = 0;
	private String url = null;
	private ArrayList<String[]> files = null;
	private boolean diagnostics = false;

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

		if (diagnostics) {
			printString(varname);
		}

		if (file == null || varname == null)
			return variable;

		try {
			for (String[] f: this.files) {
				Pattern getVariableArray = Pattern.compile(varname + "\\s*=\\s*\\[[^\\[\\]]*\\]");
				Pattern getVariableQuotes = Pattern.compile(varname + "\\s*=\\s*[\"\'][^\"\']+[\"\']");
				Matcher m = getVariableArray.matcher(f[0]);
				Matcher m2 = getVariableQuotes.matcher(f[0]);

				if (m.find()) {
					variable = Arrays.asList(m.group().replaceAll(varname+"\\s*=\\s*\\[", "").replaceAll("(]\"\')", "").split(",\n"));
				} else if (m2.find()) {
					variable.add(m2.group().replaceAll(varname+"\\s*=\\s*", "").replaceAll("[\"\']", ""));
				}

				if (diagnostics) {
					printString(varname);
					for (String s : variable)
						printString(s);
				}

			}
		} catch (PatternSyntaxException e) {
			// Occasionally comes up for variables stored in maps, e.g. version: versions['hibernate-core']
			// Quite difficult to avoid with regex, add to threats to validity
		}
		return variable;
	}

	//
	private List<String> getVersionFromVariable (String file, List<String> ext, String var, String url) {
		String variable = var.replaceAll("([\\$\\{\\}]|project\\.|ext\\.|rootProject\\.)", "");
		if (variable.isEmpty()){
			return new ArrayList<String>();
		}

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
					ext.add(CollectFileFromURL.getFile(CollectFileFromURL.urlGithubPlainText(url) + temp));
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
				rawVersions.add(mmap.group().replaceAll("(version:|[\\s\\(\\)\\[\\]\\{\\}])", ""));

				if (diagnostics)
					printString(rawVersions.get(rawVersions.size()-1));

			} 
			// Version uses concatenation
			else if (mcat.find()) {
				String[] t = mcat.group().split("\\+");
				rawVersions.add("$"+t[t.length-1].trim());

			} 
			// Version is written as a string
			else if (m.find()) { 
				try {
					rawVersions.add(m.group().split(":")[2]);
					while(m.find()) {
						rawVersions.add(m.group().split(":")[2]);
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					// Ignore these, picks up the occasional version but false positive rate far too high
					// Generally this is triggered when there is no version number, which pertains to things like exclude
					// Which for the purpose of this study we don't want to include
				}

			} 
			// Line lists variables which need resolving
			else if (mvariable.find()) { 
				String[] temp = mvariable.group().split("[,\\s]+");
				for (int i=1; i < temp.length; i++) {
					List<String> temp2 = getVariable(file, temp[i]);
					if (!temp2.isEmpty()) {
						if (diagnostics) {
							for (String s: temp2)
								printString(s + " is from mvariable raw find");
						}

						for (int j=0; j < temp2.size(); j++) {
							resolvedVersions.addAll(getVersionNum("'"+temp2.get(j)+"'", file, url));
						}
					} else { // No variable found
						unfoundVariables++;
						printString(temp[i] + " has not been found in " + url);
					}

				}

			} 
			// None of the above, empty list returned
			else { 
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

				// Captures ivy style 'latest'
				m = MAJOR.matcher(tot);
				if (m.find()) {
					resolvedVersions.add(m.group());
					continue;
				}

				// Resolves variables into versions i.e. ${springVersion}
				m = VARIABLE_VERSION.matcher(tot);
				if (m.find()) { 
					String t = m.group();
					List<String> temp = getVersionFromVariable(file, ext, t, url);
					if (!temp.isEmpty()) {
						resolvedVersions.addAll(temp);
					}
					else {
						unfoundVariables++;
						printString(t + " has not been found in " + url);
					}
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

	// Set of functions used for version ranges using less than and greater than 
	// Helper
	private int[] padWith0s (String[] arr, int desiredSize) {
		int[] temp = new int[desiredSize];
		for (int i = 0; i < arr.length; i++) {
			try {
				temp[i] = Integer.parseInt(arr[i]);
			} catch (NumberFormatException e) {
				System.err.println(arr.toString() + " was tried to cast to int");
				return null;
			}
		} for (int i = arr.length; i < desiredSize; i++) {
			temp[i] = 0;
		}
		return temp;
	}

	// Ranges come here for classification
	private String rangeLogic (String[] parts) {

		// Filters forced fixed versions e.g. (1.2.0)
		if (parts.length == 1) {
			return "fixed";
		}

		// Gets version numbers within the range
		// If an exclusive version range is used, changes range values to work with inclusive range logic
		ArrayList<int[]> preprocessed = new ArrayList<>();

		for (int i = 0; i < parts.length; i++) {
			Matcher m = PNUMBER.matcher(parts[i]);
			String temp;
			if (m.find()) {
				temp = m.group();
			} else {
				temp = "0.0.0";
			}

			// Make sure version number has 3 parts and cast to ints
			int[] temparray = padWith0s(temp.split("\\."), 3);
			if (temparray == null) {
				return "";
			}
			preprocessed.add(temparray);

			// Checks for excluded ranges and adjusts numbers to suit
			if (parts[i].endsWith(")")) {
				if (preprocessed.get(i)[1] == 0) {
					preprocessed.get(i)[0] -= 1;
					preprocessed.get(i)[1] = 0xFFFFFFF;
					preprocessed.get(i)[2] = 0xFFFFFFF;	
				} else if (preprocessed.get(i)[2] == 0) {
					preprocessed.get(i)[1] -= 1;
					preprocessed.get(i)[2] = 0xFFFFFFF;
				}
			}
		}

		// Solve for simple 2 number range
		if (preprocessed.size() == 2) {
			int[] first = preprocessed.get(0);
			int[] second = preprocessed.get(1);
			if (second[0] > first[0]) {
				return "major";
			} else if (second[1] > first[1]) {
				return "minor";
			} else if (second[2] > first[2]) {
				return "micro";
			} else {
				for (String i: parts)
					System.err.print(i + " ");
				System.err.println("could not be classified"); // Was not invoked in practice
			}
		} else {
			for (String i: parts)
				System.out.print(i + " ");
			System.out.println("classified as other");
			return "range";
		}
		return "";
	}

	private String processVersion(String version) {

		if (Pattern.matches("file(s|Tree)", version)) {
			return "file";
		} else if (Pattern.matches("\\(.*[a-zA-Z].*\\)", version)) {
			return "method";
		}

		Matcher m = RANGE.matcher(version);
		if (m.find())
			return rangeLogic(m.group().split(","));

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

	// Read in file to string
	private String getFile (File f) {
		try(BufferedReader br = new BufferedReader(new FileReader(f))) {
			// Read in file into a single string
			String line;
			StringBuilder temp = new StringBuilder();
			while((line=br.readLine()) != null) {
				temp.append(line);
				temp.append(System.getProperty("line.separator"));
			}
			return temp.toString();
		} catch (Exception e) { 
			System.err.println(e);
		}
		return "";
	}

	// Traverse file tree to collect all files
	private void getFilesRec (File f, List<String[]> list, File basedir) {
		if (f.isDirectory()) {
			for (File fnew: f.listFiles()) {
				getFilesRec(fnew, list, basedir);
			}
		} else if (f.isFile()) {
			// Add subdirectories onto the original url
			String url = list.get(0)[1].split("[\\\\/]build\\.gradle")[0] + 
					f.getAbsolutePath().split(Pattern.quote(basedir.getAbsolutePath()))[1].replace("\\", "/");
			list.add(new String[]{getFile(f), url});
		}
	}


	// Get submodule files from file system
	private ArrayList<String[]> gatherSubfiles (String originalfile, String url) {

		// Record files and urls
		String newurl = url.replace("https://github.com/", "").replace("/", "+").replace("build.gradle", "");
		ArrayList<String[]> filestoprocess = new ArrayList<>();
		filestoprocess.add(new String[]{originalfile, CollectFileFromURL.urlGithubPlainText(url)});

		// Find subfiles
		File basedir = new File("D://Build Scripts/gradle/"+newurl.charAt(0)+"/"+newurl.substring(0, newurl.length()-1)+"/");
		getFilesRec(basedir, filestoprocess, basedir);

		return filestoprocess;
	}


	// ENTRY POINT INTO CLASS. PROVIDE ENTIRE GRADLE FILE AS A STRING ALONG WITH URL FOR RECORDING
	@Override
	public void findVersionData(String originalfile, String url) {
		ArrayList<String[]> filestoprocess = gatherSubfiles(originalfile, url);
		this.files = filestoprocess;

		// Counts per project
		int files = 0;
		int methods = 0;
		int fixedVersions = 0;
		int microVersions = 0;
		int minorVersions = 0;
		int majorVersions = 0;	
		int rangeVersions = 0;
		int deplines = 0;
		ArrayList<String[]> individualEntries = new ArrayList<>();

		// Analyse each gradle file in the project
		for (String[] file: filestoprocess) {
			this.url = file[1];

			// Get dependencies closure		
			String deps = getBaseDependenciesClosure(file[0]);

			if (diagnostics) {
				printString(this.url);
				//printString(file[0]);
				printString(deps);
				printString("\n");
			}

			deplines += (deps.isEmpty()) ? 0 : deps.split("\n").length;

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
					if (diagnostics) {
						printString(line + " is a file");
					}
				} else if (Pattern.matches(lastCommand+"\\s+\\w+\\(\\).*", line)) {
					// Counts method calls
					methods++;
					if (diagnostics) {
						printString(line + " is a method");
					}
				} else {
					// Extracts version information out of line
					// When no version information is found, empty list is returned
					List<String> version = getVersionNum(line, file[0], file[1]);
					for (String v: version) {
						// Figure out what the version is, skip if it is not usable
						String result = processVersion(v);
						if (result == null) {
							continue;
						}

						// Tally
						switch(result) {
						case "file": {
							files++;
							break;
						}
						case "method": {
							methods++;
							break;
						}
						case "fixed": {
							fixedVersions++; 
							if (diagnostics) {
								printString(line + " is a fixed");
							}
							break;
						} case "micro": {
							microVersions++; 
							if (diagnostics) {
								printString(line + " is a micro");
							}
							break;
						} case "minor": {
							minorVersions++; 
							if (diagnostics) {
								printString(line + " is a minor");
							}
							break;
						} case "major": {
							majorVersions++; 
							if (diagnostics) {
								printString(line + " is a major");
							}
							break;
						} case "range": {
							rangeVersions++; 
							if (diagnostics) {
								printString(url);
								printString(deps);
								printString(line + " " + version+ " "+ "is a range");
							}
							break;
						}}


						// Save for later DB entry
						individualEntries.add(new String[]{lastCommand, result, v});
					}
				}
			}
			if (diagnostics) {
				printString("");
			}
		}

		// Add to database, first to gradlefiles, then the individual results to gradleentries
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("INSERT into gradlefileswithsub (url, fixed, micro, minor, major, nrange, "
					+ "lines, files, methods, subfiles) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			ps.setString(1, url);
			ps.setInt(2, fixedVersions);
			ps.setInt(3, microVersions);
			ps.setInt(4, minorVersions);
			ps.setInt(5, majorVersions);
			ps.setInt(6, rangeVersions);
			ps.setInt(7, deplines);
			ps.setInt(8, files);
			ps.setInt(9, methods);
			ps.setInt(10, filestoprocess.size()-1);
			ps.execute();

			for (String[] entry: individualEntries) {
				ps = c.prepareStatement("INSERT into gradleentrieswithsub (url, command, versiontype, raw) "
						+ "VALUES (?, ?, ?, ?)");
				ps.setString(1, url);
				ps.setString(2, entry[0]);
				ps.setString(3, entry[1]);
				ps.setString(4, entry[2]);
				ps.execute();
			}
		} catch (SQLException e) {
			// System.err.println(e.getMessage());
			// Will trigger if the url is already in the DB, which will avoid duplicate information
		}
	}

}
