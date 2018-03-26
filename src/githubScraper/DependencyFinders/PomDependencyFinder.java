package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class PomDependencyFinder implements DependencyFinder {

	Connection c;
	Writer out;
	String globalfile;

	// REGEX PATTERNS
	private final Pattern PRANGE = Pattern.compile("^[\\)\\(\\[\\]][^\\(\\[,]*,[^\\(\\[,]*[\\)\\(\\[\\]]$");
	private final Pattern PLATEST = Pattern.compile("^(latest|release)$");
	private final Pattern POPENRANGE = Pattern.compile("^[\\(\\)\\[\\]]$");

	private final Pattern PVARIABLE = Pattern.compile("\\$\\{[^\\}]*\\}");


	public PomDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	private synchronized void printString(String s) {
		try {
			out.write(s);
			out.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
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

	// Increments deps with the version given
	private void resolveVersion(String version, int[] deps) {
		if (version == null) {
			printString("version was null");
			return;
		}

		Matcher m = PRANGE.matcher(version);
		Matcher mlat = PLATEST.matcher(version.toLowerCase());
		if (m.find()) {
			// TODO further processing for ranges
			//printString(version + " is a range");

			String[] parts = m.group().split(",");
			for (String p : parts)
				printString(p);

			Matcher mopen = POPENRANGE.matcher(parts[1]);
			if (mopen.find())
				//printString(version + " is a major range");



		} else if (mlat.find()) {
			// TODO further processing for ranges
			//printString(version + " is a major range");
		} else {
			// TODO version is fixed
			//printString(version + " is a fixed version");
		}


	}

	// Resolves variables
	private String findVariable(Document doc, Node node) {
		String location = node.getNodeValue().substring(2, node.getNodeValue().length()-1).replaceAll("(project\\.|pom\\.)", "");
		try {
			location = doc.getElementsByTagName(location).item(0).getChildNodes().item(0).getNodeValue();

			// There are sometimes nested variables, this catches them
			Matcher m = PVARIABLE.matcher(location);
			while(m.find()) {
				String temp = m.group();
				temp = temp.substring(2, temp.length()-1);
				m = PVARIABLE.matcher(temp);
				location = location.replaceAll(temp, doc.getElementsByTagName(location).item(0).getChildNodes().item(0).getNodeValue());
			}
		} catch (Exception e) {
			printString(e.getMessage());
			printString(globalfile);
		}
		return location;
	}

	// Traverses DOM and finds version data which is sends for classification
	private void walkThroughDOM (NodeList nl, int index, Document doc, int[] dependencies) throws DOMException, IOException {
		if (index < nl.getLength())	{
			Node node = nl.item(index);
			if (node.getNodeType() == 1 && node.getNodeName().equals("version")) {
				node = node.getFirstChild();
				String version;
				if (node.getNodeValue().contains("$")) {
					version = findVariable(doc, node).trim();
				} else {
					version = node.getNodeValue().trim();
				}
				resolveVersion(version, dependencies);
			}
			else if (node.getNodeType() == 1)
				walkThroughDOM(node.getChildNodes(), 0, doc, dependencies);
			walkThroughDOM(nl, index+1, doc, dependencies);
		}
	}

	// Entry point
	public void findVersionData(String file, String url) {
		globalfile = file;

		int[] dependencies = new int[4]; // fixed, micro, minor, major

		// Create DOM from XML file and traverse
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;

		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(file)));
			doc.getDocumentElement().normalize();
			walkThroughDOM(doc.getElementsByTagName("dependencies").item(0).getChildNodes(), 0, doc, dependencies);
		} catch (SAXException | ParserConfigurationException | IOException e1) {
			//System.out.println(e1.getMessage());
		} catch (NullPointerException e2) {
			//System.out.println("Does not have any dependencies");
		}

		// Save data on DB
		// TODO


	}

}
