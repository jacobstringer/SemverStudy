package analysis_DependencyFinders;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
	private final Pattern POPENRANGE = Pattern.compile("^\\s*[\\(\\)\\[\\]]\\s*$");

	private final Pattern PVARIABLE = Pattern.compile("\\$\\{[^\\}]*\\}");
	private final Pattern PNUMBER = Pattern.compile("\\d+(\\.\\d+){0,2}");
	private final Pattern PFIXED = Pattern.compile("\\[\\d+(\\.\\d+){0,2}]");
	private final Pattern PNUMBERONLY = Pattern.compile("^\\d+(\\.\\d+){0,2}");


	public PomDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	// Diagnostics
	private boolean newLine = true;
	private synchronized void printString(String s) {
		try {
			out.write(s);
			if (newLine)
				out.write("\n");
			else
				out.write(" ");
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	private void rangeLogic (String[] parts, int[] deps) {
		
		// Filters forced fixed versions e.g. (1.2.0)
		if (parts.length == 1) {
			deps[0] += 1;
			return;
		}
	
		// Gets version numbers within the range
		// If an exclusive version range is used, changes range values to work with inclusive range logic
		ArrayList<int[]> preprocessed = new ArrayList<>();

		//
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
				return;
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
				deps[3] += 1;
			} else if (second[1] > first[1]) {
				deps[2] += 1;
			} else if (second[2] > first[2]) {
				deps[1] += 1;
			} else {
				for (String i: parts)
					System.err.print(i + " ");
				System.err.println("could not be classified"); // Was not invoked in practice
			}
		} else {
			deps[4] += 1;
			for (String i: parts)
				System.out.print(i + " ");
			System.out.println("classified as other");
		}
	}

	// Increments deps with the version given
	private void resolveVersion(String version, int[] deps) {
		// deps = soft, micro, minor, major, compositerange, fixed
		
		if (version == null) {
			printString("version was null");
			return;
		}

		version = version.trim();

		Matcher mfixed = PFIXED.matcher(version);
		Matcher m = PRANGE.matcher(version);
		Matcher mlat = PLATEST.matcher(version.toLowerCase());
		Matcher msoft = PNUMBERONLY.matcher(version);
		if (mfixed.find()) { // Hard fixed version
			deps[5]++;
		} else if (m.find()) {
			String[] parts = m.group().split(",");

			Matcher mopen = POPENRANGE.matcher(parts[1]);
			if (mopen.find()) { // No end range specified = major
				deps[3] += 1;
			} else { // Additional range logic required
				rangeLogic(parts, deps);
			}
			
		} else if (mlat.find()) { // Latest, thus major
			deps[3]++;
		} else if (msoft.find()) { // Soft version
			deps[0]++;
		} else {
			try {
				out.write(version);
				out.write("\n");
			} catch (IOException e) {}
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
		int[] dependencies = new int[6]; // soft, micro, minor, major, compositerange, fixed
		
		if (file.isEmpty())
			return;
		else {
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
		}
		
//		// Save data on DB
//		PreparedStatement ps = null;
//		try {
//			ps = c.prepareStatement("INSERT into pomnew (url, soft, micro, minor, major, other, fixed)"
//					+ " VALUES (?, ?, ?, ?, ?, ?, ?)");
//			ps.setString(1, url);
//			for (int i = 0; i < dependencies.length; i++)
//				ps.setInt(i+2, dependencies[i]);
//			ps.execute();
//		} catch (SQLException e1) {
//			e1.getMessage();
//			e1.printStackTrace();
//		}


	}

}
