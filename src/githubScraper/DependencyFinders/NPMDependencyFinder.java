package githubScraper.DependencyFinders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class NPMDependencyFinder implements DependencyFinder {
	Connection c;
	Writer out;

	public NPMDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	// REGEX PATTERNS FOR DEPENDENCIES USING SEMVER
	private final Pattern p1 = Pattern.compile("^[=\\s]*\\d+\\.\\d+\\.\\d+([-\\.\\w]+)?$");
	private final Pattern padvrange = Pattern.compile("\\|\\|");
	private final Pattern prange1 = Pattern.compile("<=?.*>=?|>=?.*<=?");
	private final Pattern prange2 = Pattern.compile("^[=\\s]*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?\\s+" // 1.2.3 - 1.2.8
			+ "-\\s+[=\\s]*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?$");
	private final Pattern plt = Pattern.compile("<\\s*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?$"); // Requires further processing
	private final Pattern pgt = Pattern.compile(">\\s*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?$"); 
	private final Pattern plteq = Pattern.compile("<=\\s*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?$"); // Requires further processing
	private final Pattern pgteq = Pattern.compile(">=\\s*[xX*\\d]+(\\.[xX*\\d]+){0,2}([-\\.\\w]+)?$");
	private final Pattern pmicro = Pattern.compile("~\\s*\\d+(\\.\\d+){2}"
			+ "|^[=~\\s]*\\d+\\.\\d+(\\.[xX*])?$");
	private final Pattern pminor = Pattern.compile("\\^\\s*\\d+(\\.[xX*\\d]+){0,2}" // ^1.whatever or ^1
			+ "|^[=\\s]*\\d+.[xX*]+(\\.[xX*\\d]+)?" // 1.x.whatever
			+ "|^[=\\s\\~]*\\d+(\\.[xX*\\d]+)?$"); // ~1 or ~1.2 or ~1.x
	private final Pattern pmajor = Pattern.compile("^[=\\s]*[*xX](\\.[\\d+xX*])?(\\.[\\d+xX*])?$"
			+ "|^latest$");
	private final Pattern pgit = Pattern.compile("^git.*://"); // Requires further processing
	private final Pattern purl = Pattern.compile("^https?://"); // Requires further processing
	private final Pattern pgithub = Pattern.compile("^[\\w/]+$"); // Requires further processing
	private final Pattern pfile = Pattern.compile("^file:"); 

	// Classifies the version, returns false if it could not be classified
	private DepType classifyRange(String info_old) throws IOException {
		DepType results = new DepType();
		if (info_old.equals("") || info_old.equals(">=")) {
			return results;
		}

		// Cleans up whitespace and superfluous 'v'. Keeps old string in case it is a url
		String info = info_old.replace("v", "").trim();

		// Matchers
		Matcher mrange1 = prange1.matcher(info);
		Matcher mrange2 = prange2.matcher(info);
		Matcher mlt = plt.matcher(info);
		Matcher mgt = pgt.matcher(info);
		Matcher mgteq = pgteq.matcher(info);
		Matcher mlteq = plteq.matcher(info);
		Matcher mmicro = pmicro.matcher(info);
		Matcher mminor = pminor.matcher(info);
		Matcher mmajor = pmajor.matcher(info);
		Matcher m1 = p1.matcher(info);
		Matcher mgit = pgit.matcher(info);
		Matcher murl = purl.matcher(info);
		Matcher mgithub = pgithub.matcher(info);
		Matcher mfile = pfile.matcher(info);
		Matcher madvrange = padvrange.matcher(info);

		if (mrange1.find()) {
			//results = rangelogic(info, true);
			//out.write(info_old + " " + results.toString() + "\n");
		} else if (mrange2.find()) {
			//results = rangelogic(info, false);
			//out.write(info_old + " " + results.toString() + '\n');
		} else if (madvrange.find()) {
			String[] ranges = info.split("\\s*\\|\\|\\s*");
			DepType temp; 
			for (int i = 0; i < ranges.length; i++) {
				temp = classifyRange(ranges[i]);
				results = (results.compareTo(temp) > 0) ? results : temp;
			}
			out.write(info_old + " " + results.toString() + '\n');
		} else if (mlt.find()) {
			//TODO
			//out.write(info + " plt: " + mlt.group() + '\n');
		} else if (mgt.find()) {
			results.major = true;
			//out.write(info + " pgt: " + mgt.group() + '\n');
		} else if (mgteq.find()) {
			results.major = true;
			//out.write(info + " pgteq: " + mgteq.group() + '\n');
		} else if (mlteq.find()) {
			//TODO
			//out.write(info + " plteq: " + mlteq.group() + '\n');
		} else if (mmicro.find()) {
			results.micro = true;
			//out.write(info + " pmicro: " + mmicro.group() + '\n');
		} else if (mminor.find()) {
			results.minor = true;
			//out.write(info + " pminor: " + mminor.group() + '\n');
		} else if (mmajor.find()) {
			results.major = true;
			//out.write(info + " pmajor: " + mmajor.group() + '\n');
		} else if (m1.find()) {
			results.noRange = true;
			//out.write(info + " p1: " + m1.group() + '\n');
		} /*else if (mgit.find()) {
			out.write(info + " pgit: " + mgit.group() + '\n');
		} else if (murl.find()) {
			out.write(info + " purl: " + murl.group() + '\n');
		} else if (mgithub.find()) {
			out.write(info + " GITHUB!!!!: " + mgithub.group() + '\n');
		} else if (mfile.find()) {
			out.write(info + " file: " + mfile.group() + '\n');
		} else {
			out.write(info + " WAS NOT MATCHED\n");
		}*/ 

		return results;
	}

	// Set of functions used for version ranges using less than and greater than 
	private String[] padWith0s (String[] arr, int desiredSize) {
		if (arr.length < desiredSize) {
			String[] temp = new String[desiredSize];
			for (int i = 0; i < arr.length; i++) {
				temp[i] = arr[i];
			} for (int i = arr.length; i < desiredSize; i++) {
				temp[i] = "0";
			}
			arr = temp;
		}
		return arr;
	}

	private boolean[] testBeforeCastingToInt (String[] arr) {
		boolean[] flags = new boolean[arr.length];
		for (int i = 0; i < arr.length; i++) {
			try {
				Integer.parseInt(arr[i]);
				flags[i] = true;
			} catch (Exception e) {
				flags[i] = false;
			}
		}
		return flags;
	}

	private DepType rangelogic (String info, boolean range1) throws IOException {
		DepType dep = new DepType();
		String[] versions;
		boolean equal = true;

		if (range1) {
			versions = info.replace(">", "").replace("<", "").replace("=", "").trim().split("\\s+");
			if (info.charAt(0) == '<') {
				dep.major = true;
				return dep;
			}
			equal = info.charAt(info.indexOf('<')+1) == '='; // We don't care if it is >= or >
		} else {
			versions = info.trim().split("\\s+-\\s+");
		}

		if (versions.length == 2) { // To make sure it splits properly
			// Split ranges into individual numbers, padding with 0s where necessary
			String[] first = padWith0s(versions[0].split("\\."), 3);//.toString();
			String[] second = padWith0s(versions[1].split("\\."), 3);//.toString();

			// Strip prerelease tags
			first[2] = first[2].replaceAll("[^xX*0-9]+", "");
			second[2] = second[2].replaceAll("[^xX*0-9]+", "");

			// Checks which are ints
			boolean[] isNum1 = testBeforeCastingToInt(first);
			boolean[] isNum2 = testBeforeCastingToInt(second);

			// Ascertain how far apart the ranges are
			int major = 0, minor = 0, micro = 0;

			// Test major
			if (isNum1[0] && isNum2[0]) { // Both are numbers
				major = Integer.parseInt(second[0]) - Integer.parseInt(first[0]);
				if (major > 0 && equal || major > 1 || major == 1 && (!second[1].equals("0") || !second[2].equals("0"))) {
					dep.majorRange = true;
					return dep;
				} else if (major > 0) {
					dep.minorRangeSimplifiable = true;
					return dep;
				}
			} else if (!isNum2[0]) { // Second range is wildcard
				dep.majorRange = true;
				return dep;
			} else if (!isNum1[0]) { // First range is wildcard
				if (!second[0].equals("0") && !(!second[0].equals("1") && !equal)) {
					dep.majorRange = true;
					return dep;
				}
			}

			// Test minor
			if (isNum1[1] && isNum2[1]) { // Both are numbers
				minor = Integer.parseInt(second[1]) - Integer.parseInt(first[1]);
				if (minor > 0 && equal || minor > 1 || minor == 1 && !second[2].equals("0")) {
					dep.minorRange = true;
					return dep;
				} else if (minor > 0) {
					dep.microRangeSimplifiable = true;
					return dep;
				}
			} else if (!isNum2[1]) { // Second range is wildcard
				dep.minorRange = true;
				return dep;
			} else if (!isNum1[1]) { // First range is wildcard
				if (!second[1].equals("0") && !(!second[1].equals("1") && !equal)) {
					dep.minorRange = true;
					return dep;
				}
			}

			// Test micro
			if (isNum1[2] && isNum2[2]) { // Both are numbers
				micro = Integer.parseInt(second[2]) - Integer.parseInt(first[2]);
				if (micro > 900) {
					dep.microRangeSimplifiable = true;
					return dep;
				} else if (micro > 0 && equal || micro > 1) {
					dep.microRange = true;
					return dep;
				} else {
					dep.noRange = true;
					return dep;
				}
			} else if (!isNum2[2]) { // Second range is wildcard
				dep.microRange = true;
				return dep;
			} else if (!isNum1[2]) { // First range is wildcard
				if (!second[2].equals("0") && !(!second[2].equals("1") && !equal)) {
					dep.microRange = true;
					return dep;
				}
			} 

			// None of the above worked, the range is zero
			dep.noRange = true;

		} else {
			out.write(info + " was not split into 2 parts");
		}
		return dep;
	}

	public int[] findVersionData(String file) {return new int[1];}
	public int[] findVersionData(String file, String url) throws IOException {
		// Return int[]{Major[0], Minor, Micro, Non-Semantic, MajorRange[4], MinorRange, MicroRange, SpecificRange
		// MajorDev[8], MinorDev, MicroDev, Non-SemanticDev, MajorRange[12], MinorRange, MicroRange, SpecificRange
		// Additional Build Files[16], Environment Variables[17}
		int[] info = new int[18];

		// Pass string into JSON Object to work
		JSONObject json = null;
		try {
			json = new JSONObject(file);
		} catch (Exception e) {
			//out.write("\n\n\n" + file + " is not a JSONObject!\n\n\n");
			return info;
		}

		// Find dependencies
		JSONObject dep = null;
		try {
			dep = (JSONObject)json.opt("dependencies");
		} catch (Exception e) {
			//out.write("\n\n\n" + json.opt("dependencies") + " is not a JSONObject!\n\n\n");
			return info;
		}
		if (dep != null) {
			Iterator<String> keys = dep.keys();
			while (keys.hasNext()){
				try {
					String versionrange = (String)dep.get(keys.next());
					//out.write(versionrange.toString() + "\n");
					classifyRange(versionrange);
				} catch (ClassCastException e) {}
			}
		}

		/*
		// Find developer dependencies
		dep = (JSONObject)json.opt("devDependencies");
		if (dep != null) {
			Iterator<String> keys = dep.keys();
			while (keys.hasNext()){
				String versionrange = dep.getString(keys.next()).trim();
				out.write(versionrange);
			}
		}
		 */

		return info;
	}

}

class DepType implements Comparable<DepType> {
	public boolean major = false;
	public boolean minor = false;
	public boolean micro = false;
	public boolean noRange = false;
	public boolean majorRange = false;
	public boolean minorRange = false;
	public boolean microRange = false;
	public boolean minorRangeSimplifiable = false;
	public boolean microRangeSimplifiable = false;
	/*public boolean  = false;
	public boolean  = false;*/

	public String toString() {
		if (major) return "major";
		if (minor) return "minor";
		if (micro) return "micro";
		if (noRange) return "noRange";
		if (majorRange) return "majorRange";
		if (minorRange) return "minorRange";
		if (microRange) return "microRange";
		if (minorRangeSimplifiable) return "minorRangeSimplifiable";
		if (microRangeSimplifiable) return "microRangeSimplifiable";
		return "none";
	}

	public int toRank() {
		int temp = 0;
		if (majorRange) temp = 8;
		else if (major) temp = 7;
		else if (minorRange) temp = 6;
		else if (minorRangeSimplifiable) temp = 5;
		else if (minor) temp = 4;
		else if (microRange) temp = 3;
		else if (microRangeSimplifiable) temp = 2;
		else if (micro) temp = 1;
		return temp;
	}

	@Override
	public int compareTo(DepType arg0) {
		return this.toRank() - arg0.toRank();
	}
}
