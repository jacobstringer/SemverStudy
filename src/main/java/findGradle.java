import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.sql.*;

public class findGradle {

	// CHANGE THESE FOUR VARIABLES ONLY
	private static String zip = "E:/Build Scripts/gradle.zip";
    
    private static String[] projects = {"Spring"};

            /*{
        "Junit",
        "SLF4J",
        "Scala",
        "Guava",
        "Apache",
        "Clojure",
        "Log4j",
        "Logback",
        "Mockito",
        "Jackson",
        "Servlet",
        "Spring",
        "TestNG",
        "AppCompat",
        "Gson",
        "NREPL",
        "Joda",
        "OSGi",
        "FindBugs",
        "Maven",
        "AssertJ",
        "Hamcrest",
        "EasyMock",
        "Lombok",
        "Javax",
        "Inject",
        "Groovy"
    };*/
    
	public static void main(String[] args) {
        // DB
		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e2) {
			e2.printStackTrace();
		}
        
        Connection c = null;
        try {
            c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/BuildData", "postgres", "password");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
		try (ZipFile zf = new java.util.zip.ZipFile(zip)) {
			for (Enumeration<? extends ZipEntry> entries = zf.entries(); entries.hasMoreElements();) {								
				// Read in file and place on queue
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory())
					continue;

                String[] path = entry.getName().split("/");
                String url = path[path.length-1];
                for (String i: projects)
                    if (url.contains(i)) {
                        System.err.println(url);
                        String pk = "https://github.com/" + url.replaceAll("build\\.gradle", "").replaceAll("\\+", "/");
                        try {
                            PreparedStatement ps = c.prepareCall("SELECT * FROM dependencies WHERE url = ? and not fork");
                            ps.setString(1, pk);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                System.out.println(url);
                            }
                        } catch (SQLException e1) {
                            e1.getMessage();
                            e1.printStackTrace();
                        }
                    }  
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
