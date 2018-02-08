package githubScraper.DependencyFinders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class NPMDependencyFinder implements DependencyFinder {
	Connection c;

	public NPMDependencyFinder(Connection c) {
		this.c = c;
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

	// LT Patterns
	private final Pattern pminorsimp = Pattern.compile("^<\\s*1(.0){0,2}$");
	private final Pattern pmicrosimp = Pattern.compile("^<\\s*0.1(.0){0,1}$");
	private final Pattern pmajorlt = Pattern.compile("^<\\s*[xX*2-9][0-9]*(.[xX*\\d]+){0,2}"
			+ "|^<\\s*1.[xX*1-9]"
			+ "|^<\\s*1.0.[1-9]");
	private final Pattern pminorlt = Pattern.compile("^<\\s*0.[xX*2-9]"
			+ "|^<\\s*1"
			+ "|^<\\s*0.1.[xX*1-9]");

	//LTEQ Patterns
	private final Pattern pmajorlteq = Pattern.compile("^<=\\s*[xX*1-9][0-9]*(.[xX*\\d]+){0,2}");
	private final Pattern pminorlteq = Pattern.compile("^<=\\s*0.[xX*1-9]");

	// Classifies the version, returns false if it could not be classified
	private DepType classifyRange(String info_old, String url) {
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

		// Categorise
		if (madvrange.find()) {
			String[] ranges = info.split("\\s*\\|\\|\\s*");
			DepType temp; 
			for (int i = 0; i < ranges.length; i++) {
				temp = classifyRange(ranges[i], url);
				results = (results.compareTo(temp) > 0) ? results : temp;
			}
			results.composite = true;
			extraDB(results, "npmcomposite", url, info_old);
		} else if (mrange2.find()) {
			results = rangelogic(info, false);
			extraDB(results, "npmrange", url, info_old);
		} else if (mrange1.find()) {
			results = rangelogic(info, true);
			extraDB(results, "npmrange", url, info_old);
		} else if (mlt.find()) {
			results = lt(info, results);
		} else if (mgteq.find() || mgt.find()) {
			results.gt = true;
		} else if (mlteq.find()) {
			results = lteq(info, results);
		} else if (mmicro.find()) {
			results.micro = true;
		} else if (mminor.find()) {
			results.minor = true;
		} else if (mmajor.find()) {
			results.major = true;
		} else if (m1.find()) {
			results.noRange = true;
		} else if (mgit.find() || mgithub.find()) {
			results.git = true;
			extraDB(results, "npmgit", url, info_old);
		} else if (murl.find()) {
			results.url = true;
			extraDB(results, "npmurl", url, info_old);
		} else if (mfile.find()) {
			results.file = true;
			extraDB(results, "npmfile", url, info_old);
		} else {
			extraDB(results, "npmunmatched", url, info_old);
		}
		return results;
	}

	// Add to DB version ranges, composites, files, and unmatched
	private void extraDB (DepType results, String table, String url, String info_old) {
		// Check for repeats
		try {
			PreparedStatement query = c.prepareStatement("SELECT * FROM "+table+" WHERE url=? AND dep=?");
			query.setString(1, url);
			query.setString(2, info_old);
			ResultSet rs = query.executeQuery();
			if (rs.isBeforeFirst()) {
				return;
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		
		// Add info
		PreparedStatement ps = null;
		try {
			if(table.equals("npmcomposite") || table.equals("npmrange")) {
				ps = c.prepareStatement("INSERT into "+table+" (url, dep, micro, minor, major) VALUES (?, ?, ?, ?, ?)");
				ps.setString(1, url);
				ps.setString(2, info_old);
				ps.setBoolean(3, results.micro);
				ps.setBoolean(4, results.minor);
				ps.setBoolean(5, results.major);
				ps.execute();
			} else {
				ps = c.prepareStatement("INSERT into "+table+" (url, dep) VALUES (?, ?)");
				ps.setString(1, url);
				ps.setString(2, info_old);
				ps.execute();
			}
		} catch (SQLException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
			
		}
	}

	// Logic for < types
	private DepType lt (String info, DepType results) {
		results.lt = true;

		Matcher mminorsimp = pminorsimp.matcher(info);
		Matcher mmicrosimp = pmicrosimp.matcher(info);
		Matcher mmajorlt = pmajorlt.matcher(info);
		Matcher mminorlt = pminorlt.matcher(info);

		if (mminorsimp.find()) {
			results.minor = true;
			results.simplifiable = true;
		} else if (mmicrosimp.find()) {
			results.micro = true;
			results.simplifiable = true;
		} else if (mmajorlt.find()) {
			results.major = true;
		} else if (mminorlt.find()) {
			results.minor = true;
		} else {
			results.micro = true;
		}

		return results;
	}

	// Logic for <= types
	private DepType lteq (String info, DepType results) {
		results.lt = true;

		Matcher mmajorlt = pmajorlteq.matcher(info);
		Matcher mminorlt = pminorlteq.matcher(info);

		if (mmajorlt.find()) {
			results.major = true;
		} else if (mminorlt.find()) {
			results.minor = true;
		} else {
			results.micro = true;
		}

		return results;
	}

	// Set of functions used for version ranges using less than and greater than 
	// Helper
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

	// Helper
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

	// Ranges come here for classification
	private DepType rangelogic (String info, boolean range1) {
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
					dep.major = true;
					dep.range = true;
					return dep;
				} else if (major > 0) {
					dep.minor = true;
					dep.simplifiable = true;
					return dep;
				}
			} else if (!isNum2[0]) { // Second range is wildcard
				dep.major = true;
				dep.range = true;
				return dep;
			} else if (!isNum1[0]) { // First range is wildcard
				if (!second[0].equals("0") && !(!second[0].equals("1") && !equal)) {
					dep.major = true;
					dep.range = true;
					return dep;
				}
			}

			// Test minor
			if (isNum1[1] && isNum2[1]) { // Both are numbers
				minor = Integer.parseInt(second[1]) - Integer.parseInt(first[1]);
				if (minor > 0 && equal || minor > 1 || minor == 1 && !second[2].equals("0")) {
					dep.minor = true;
					dep.range = true;
					return dep;
				} else if (minor > 0) {
					dep.micro = true;
					dep.simplifiable = true;
					return dep;
				}
			} else if (!isNum2[1]) { // Second range is wildcard
				dep.minor = true;
				dep.range = true;
				return dep;
			} else if (!isNum1[1]) { // First range is wildcard
				if (!second[1].equals("0") && !(!second[1].equals("1") && !equal)) {
					dep.minor = true;
					dep.range = true;
					return dep;
				}
			}

			// Test micro
			if (isNum1[2] && isNum2[2]) { // Both are numbers
				micro = Integer.parseInt(second[2]) - Integer.parseInt(first[2]);
				if (micro > 900) {
					dep.micro = true;
					dep.simplifiable = true;
					return dep;
				} else if (micro > 0 && equal || micro > 1) {
					dep.micro = true;
					dep.range = true;
					return dep;
				} else {
					dep.noRange = true;
					return dep;
				}
			} else if (!isNum2[2]) { // Second range is wildcard
				dep.micro = true;
				dep.range = true;
				return dep;
			} else if (!isNum1[2]) { // First range is wildcard
				if (!second[2].equals("0") && !(!second[2].equals("1") && !equal)) {
					dep.micro = true;
					dep.range = true;
					return dep;
				}
			} 

			// None of the above worked, the range is zero
			dep.noRange = true;

		} else {
			System.out.println(info + " was not split into 2 parts");
		}
		return dep;
	}

	// Entry point, takes a package.json file saved as a string and a url for identification in the db
	public void findVersionData(String file, String url) {
		int[] info = new int[22];

		// Pass string into JSON Object to work
		JSONObject json = null;
		try {
			json = new JSONObject(file);
		} catch (Exception e) {
			try {
				PreparedStatement ps = c.prepareStatement("INSERT into npm (url, notjson) VALUES (?, ?)");
				ps.setString(1, url);
				ps.setBoolean(2, true);
				ps.execute();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			return;
		}

		// Find dependencies
		JSONObject dep = null;
		try {
			dep = (JSONObject)json.opt("dependencies");
			// Find dependencies
			if (dep != null) {
				Iterator<String> keys = dep.keys();
				while (keys.hasNext()){
					try {
						String versionrange = (String)dep.get(keys.next());
						classifyRange(versionrange, url).count(info);
					} catch (ClassCastException e) {}
				}
			}
		} catch (Exception e) {}

		// Find developer dependencies
		int[] infodev = new int[22];
		dep = null;
		try {
			dep = (JSONObject)json.opt("devDependencies");
			if (dep != null) {
				Iterator<String> keys = dep.keys();
				while (keys.hasNext()){
					String versionrange = dep.getString(keys.next()).trim();
					classifyRange(versionrange, url).count(infodev);
				}
			}
		} catch (Exception e) {}

		// Save information
		saveToDB(info, infodev, url);
	}

	// Saves final counts for each file to DB
	private void saveToDB(int[] info, int[]infodev, String url) {
		/* Return int[]{norange int, 0
	    micro int, 1
	    minor int, 2
	    major int, 3
	    microrange int, 4
	    minorrange int, 5
	    majorrange int, 6
	    microsimp int, 7
	    minorsimp int, 8
	    microcomp int, 9
	    minorcomp int, 10
	    majorcomp int, 11
	    microlt int, 12
	    minorlt int, 13
	    majorlt int, 14
	    microltsimp int, 15
	    minorltsimp int, 16
	    gt int, 17
	    urldep int, 18
	    git int, 19
	    filedep int, 20
	    unknowndep int, 21 }*/
		PreparedStatement ps = null;
		try {
			ps = c.prepareStatement("INSERT into npm (url, norange, micro, minor, major, microrange, "
					+ "minorrange, majorrange, microsimp, minorsimp, microcomp, minorcomp, majorcomp, "
					+ "microlt, minorlt, majorlt, microltsimp, minorltsimp, gt, urldep, git, "
					+ "filedep, unknowndep, norangedev, microdev, minordev, majordev, microrangedev, "
					+ "minorrangedev, majorrangedev, microsimpdev, minorsimpdev, microcompdev, minorcompdev, majorcompdev, "
					+ "microltdev, minorltdev, majorltdev, microltsimpdev, minorltsimpdev, gtdev, urldepdev, gitdev, "
					+ "filedepdev, unknowndepdev) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			ps.setString(1, url);
			for (int i = 2, j = 0; i < 24; i++, j++) {
				ps.setInt(i, info[j]);
			}
			for (int i = 24, j = 0; i < 46; i++, j++) {
				ps.setInt(i, infodev[j]);
			}
			ps.execute();
		} catch (SQLException e1) {
			e1.getMessage();
			e1.printStackTrace();
		}
	}

}

class DepType implements Comparable<DepType> {
	public boolean major = false;
	public boolean minor = false;
	public boolean micro = false;
	public boolean range = false;
	public boolean simplifiable = false;
	public boolean composite = false;
	public boolean noRange = false;
	public boolean lt = false;
	public boolean gt = false;
	public boolean git = false;
	public boolean file = false;
	public boolean url = false;

	public String toString() {
		String info = "";
		if (major) info += "major";
		if (minor) info += "minor";
		if (micro) info += "micro";
		if (lt) info += " lt";
		if (gt) info += " gt";
		if (noRange) info += "noRange";
		if (range) info += " range";
		if (simplifiable) info += " simplifiable";
		if (composite) info += " composite";
		if (git) info += "git";
		if (file) info += "file";
		if (url) info += "url";
		if (info.equals("")) {
			return "none";
		} else {
			return info;
		}

	}

	public int toRank() {
		int temp = 0;
		if (this.composite) temp += 1<<8;
		if (this.range) temp += 1<<7;
		if (this.simplifiable) temp += 1<<6;
		if (this.lt) temp += 1<<5;
		if (this.gt) temp += 1<<4;
		if (this.major) temp += 1<<3;
		if (this.minor) temp += 1<<2;
		if (this.micro) temp += 1<<1;
		return temp;
	}

	public void count(int[] counter) {
		/* Return int[]{norange int, 0
	    micro int, 1
	    minor int, 2
	    major int, 3
	    microrange int, 4
	    minorrange int, 5
	    majorrange int, 6
	    microsimp int, 7
	    minorsimp int, 8
	    microcomp int, 9
	    minorcomp int, 10
	    majorcomp int, 11
	    microlt int, 12
	    minorlt int, 13
	    majorlt int, 14
	    microltsimp int, 15
	    minorltsimp int, 16
	    gt int, 17
	    urldep int, 18
	    git int, 19
	    filedep int, 20
	    unknowndep int, 21 }*/
		if (this.noRange) counter[0]++;
		else if (this.major) {
			if (this.composite) counter[11]++;
			else if (this.lt) counter[14]++;
			else if (this.range) counter[6]++;
			else counter[3]++;
		} else if (this.minor) {
			if (this.composite) counter[10]++;
			else if (this.lt && this.simplifiable) counter[16]++;
			else if (this.lt) counter[13]++;
			else if (this.range) counter[5]++;
			else if (this.simplifiable) counter[8]++;
			else counter[2]++;
		} else if (this.micro) {
			if (this.composite) counter[9]++;
			else if (this.lt && this.simplifiable) counter[15]++;
			else if (this.lt) counter[12]++;
			else if (this.range) counter[4]++;
			else if (this.simplifiable) counter[7]++;
			else counter[1]++;
		}
		else if (this.file) counter[20]++;
		else if (this.git) counter[19]++;
		else if (this.url) counter[18]++;
		else if (this.gt) counter[17]++;
		else counter[21]++;

	}

	@Override
	public int compareTo(DepType arg0) {
		return this.toRank() - arg0.toRank();
	}
}
