package fileMover;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RepopulateDB {
	
	// Reconciles file system to DB - the DB is missing a number of entries of files that are present
	public static void main(String[] args) {
		// DB
		Connection c = null;
		try {
			Class.forName("org.postgresql.Driver");
			c = DriverManager.getConnection(
					"jdbc:postgresql://localhost:5432/BuildData", "postgres", "password");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName()+": "+e.getMessage());
			System.exit(0);
		}

		// For these types of files
		String[] foldersAvailable = {"package", "rake", "pom", "build", "gradle"};
		String src = "D://Build Scripts/";
		
		for (String folder: foldersAvailable) {
			// Find what files are present
			BufferedReader in = null;
			try {
				in = new BufferedReader(new FileReader(src+folder+'/'+folder+"2.csv"));
				in.readLine(); in.readLine(); // First two lines are not needed
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			// Track which files got lost
			BufferedWriter out = null;
			try {
				out = new BufferedWriter(new FileWriter(src+folder+'/'+folder+"recoveredfiles.csv"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			String line = null;
			String[] info = null;
			while (true) {
				// Read in files one at a time
				try {
					line = in.readLine();
					if (line == null) {
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				// Change back to url
				info = line.split(",");
				info = info[0].split("\\+");
				line = "https://github.com";
				for (int i = 0; i < info.length - 1; i++) line += '/'+info[i];
				
				// Check for repeats
				try {
					PreparedStatement query = c.prepareStatement("SELECT * FROM npm WHERE url=?");
					query.setString(1, line);
					ResultSet rs = query.executeQuery();
					if (rs.isBeforeFirst()) {
						continue;
					}
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				
				// By now, all entries are not in DB. Add to DB first
				try {
					PreparedStatement ps = c.prepareStatement("INSERT into dependencies (url) VALUES (?)");
					ps.setString(1, line);
					ps.execute();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				
				// Add to csv record
				try {
					out.write(line);
					out.write('\n');
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			
			// Close files
			try {
				out.close();
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
