package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;

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
	
	public PomDependencyFinder(Connection c) {
		this.c = c;
	}
	
	// Increments deps with the version given
	private void resolveVersion(String version, int[] deps) {
		//TODO classify version type
	}
	
	// Resolves variables
	private String findVariable(Document doc, Node node) {
		String location = node.getNodeValue().substring(2, node.getNodeValue().length()-1).replaceAll("project\\.", "");
		System.out.println(location);
		try {
			return doc.getElementsByTagName(location).item(0).getNodeValue();
		} catch (NullPointerException e) {
			System.out.println(e);
			return location;
		}
	}
	
	// 
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
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		int[] dependencies = new int[4]; // fixed, micro, minor, major
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(file)));
			doc.getDocumentElement().normalize();
			walkThroughDOM(doc.getElementsByTagName("dependencies").item(0).getChildNodes(), 0, doc, dependencies);
		} catch (SAXException | ParserConfigurationException | IOException e1) {
			System.out.println(e1.getMessage());
		} catch (NullPointerException e2) {
			System.out.println("Does not have any dependencies");
		}
	}

}
