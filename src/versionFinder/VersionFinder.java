package versionFinder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class VersionFinder {
	
	public String findVariable(Document doc, Node node) {
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

	public void walkThroughDOM (NodeList nl, int index, Document doc, BufferedWriter out) throws DOMException, IOException {
		if (index < nl.getLength())	{
			Node node = nl.item(index);
			if (node.getNodeType() == 1 && node.getNodeName().equals("version")) {
				node = node.getFirstChild();
				if (node.getNodeValue().contains("$"))
					out.write(findVariable(doc, node));
				else 
					out.write(node.getNodeValue().trim());
				out.write("\n");
			}
			else if (node.getNodeType() == 1)
				walkThroughDOM(node.getChildNodes(), 0, doc, out);
			walkThroughDOM(nl, index+1, doc, out);
		}
	}

	public void findVersionData(File xmlFile, BufferedWriter out) throws IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(xmlFile);
			doc.getDocumentElement().normalize();
			walkThroughDOM(doc.getElementsByTagName("dependencies").item(0).getChildNodes(), 0, doc, out);
			
		} catch (SAXException | ParserConfigurationException | IOException e1) {
			System.out.println(xmlFile.getName() + e1.getMessage());
		} catch (NullPointerException e2) {
			System.out.println(xmlFile.getName() + " does not have any dependencies");
		}
	}

	public static void main(String[] args) throws Exception {
		File folder = new File("samples/");
		File[] listOfFiles = folder.listFiles();

		VersionFinder vf = new VersionFinder();
		BufferedWriter out = new BufferedWriter(new FileWriter(new File("versions_info_test.txt")));		

		for (File file: listOfFiles) {
			System.out.println(file.getName());
			out.write(file.getName() + "\n");
			vf.findVersionData(file, out);
			out.write("\n");
		}

		out.close();

	}

}
