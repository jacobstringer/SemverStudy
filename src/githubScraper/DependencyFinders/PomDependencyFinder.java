package githubScraper.DependencyFinders;

import java.io.IOException;
import java.io.StringReader;

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
	
	private String findVariable(Document doc, Node node) {
		String location = node.getNodeValue().substring(2, node.getNodeValue().length()-1);
		System.out.println(location);
		if (location.equals("project.version") || location.equals("project.parent.version")) {
			location = "version";
		}
		try {
			return doc.getElementsByTagName(location).item(0).getNodeValue();
		} catch (NullPointerException e) {
			System.out.println(e);
			return location;
		}
	}
	
	private static boolean isSemantic (String dependency) {
		if (dependency.contains("[") || dependency.contains("]") || dependency.contains("(") || dependency.contains(")")) {
			return true;
		} else {
			return false;
		}
	}

	private void walkThroughDOM (NodeList nl, int index, Document doc, int[] dependencies) throws DOMException, IOException {
		if (index < nl.getLength())	{
			Node node = nl.item(index);
			if (node.getNodeType() == 1 && node.getNodeName().equals("version")) {
				node = node.getFirstChild();
				if (node.getNodeValue().contains("$")) {
					if (isSemantic(findVariable(doc, node))) dependencies[1]++;
					else dependencies[0]++;
				}
				else {
					if (isSemantic(node.getNodeValue().trim())) dependencies[1]++;
					else dependencies[0]++;
				}
			}
			else if (node.getNodeType() == 1)
				walkThroughDOM(node.getChildNodes(), 0, doc, dependencies);
			walkThroughDOM(nl, index+1, doc, dependencies);
		}
	}

	// Entry point
	public int[] findVersionData(String file) {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		int[] dependencies = new int[2];
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
		return dependencies;
	}

}
