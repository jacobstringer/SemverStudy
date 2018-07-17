package countSubModules;

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

	public PomDependencyFinder(Connection c, Writer out) {
		this.c = c;
		this.out = out;
	}

	// Entry point
	public void findVersionData(String file, String url) {
		globalfile = file;
		int[] dependencies = new int[5]; // fixed, micro, minor, major, compositerange
		
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
				System.out.println(doc.getElementsByTagName("modules").getLength());
			} catch (SAXException | ParserConfigurationException | IOException e1) {
				//System.out.println(e1.getMessage());
			} catch (NullPointerException e2) {
				//System.out.println("Does not have any dependencies");
			}
		}
		
//		// Save data on DB
//		PreparedStatement ps = null;
//		try {
//			ps = c.prepareStatement("UPDATE dependencies SET submodules = ? WHERE url = ?");
//			ps.setInt(1, result);
//			ps.setString(2, url);
//			ps.execute();
//		} catch (SQLException e1) {
//			e1.getMessage();
//			e1.printStackTrace();
//		}


	}

}
