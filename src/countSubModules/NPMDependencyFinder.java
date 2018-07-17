package countSubModules;

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

	// ENTRY POINT, takes a package.json file saved as a string and a url for identification in the db
	public void findVersionData(String file, String url) {
		System.out.println(file.split("require").length);
		
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
