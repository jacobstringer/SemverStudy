package dbQueriesToFiles;

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
import java.util.ArrayList;

public class QueryToFile {

	private static void getEmailList() {
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

		// Open file
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("data/emails.csv"));
			out.write("Email,url,totaldeps\n");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}

		// Get info
		PreparedStatement ps = null;
		try {
			ps = c.prepareCall("with gradle as (\r\n" + 
					"	select url, fixed+micro+minor+major+nrange+files+methods as gradsum \r\n" + 
					"    from gradlefileswithsub \r\n" + 
					"),\r\n" + 
					"\r\n" + 
					"npmabbrev as (\r\n" + 
					"	select url, norange+ micro+ minor+ major+ microrange+ minorrange+ majorrange+ microsimp+ minorsimp+microcomp+ minorcomp+ majorcomp+ microlt +minorlt +majorlt+ microltsimp +minorltsimp+ gt+ urldep+git+ filedep as npmsum \r\n" + 
					"    from npm \r\n" + 
					"),\r\n" + 
					"\r\n" + 
					"pomabbrev as (\r\n" + 
					"	select url, fixed+micro+minor+major+other as pomsum \r\n" + 
					"    from pom\r\n" + 
					"),\r\n" + 
					"\r\n" + 
					"totalabbrev as (\r\n" + 
					"    select d.url, d.users, d.latestcommit, COALESCE(pomsum,0)+COALESCE(gradsum,0)+COALESCE(npmsum,0) as total\r\n" + 
					"    from dependencies d left join gradle g on d.url = g.url left join npmabbrev n on d.url = n.url left join pomabbrev p on d.url = p.url\r\n" + 
					"    order by latestcommit desc\r\n" + 
					")\r\n" + 
					"\r\n" + 
					"select * from totalabbrev where total > 0 and users is not null");

			ResultSet rs = ps.executeQuery();
			while(rs.next()) {
				try {
					out.write(rs.getString("users") + "," + rs.getString("url") + "," + rs.getString("latestcommit") + "," + rs.getInt("total") + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			out.close();

		} catch (SQLException | IOException e) {
			System.out.println(ps.toString());
			e.printStackTrace();
		}	
	}

	private static void update() {
		// Open file
		BufferedReader in = null;
		ArrayList<String> file = new ArrayList<>();
		try {
			in = new BufferedReader(new FileReader("data/emails.csv"));
			String line;
			while ((line = in.readLine()) != null) {
				file.add(line);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Open file
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("data/emails.csv"));
			for(String line: file) {
				if (!line.split(",").equals("null")) {
					out.write(line);
					out.write("\n");
				}
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private static void printLargeCsv() {
		// Open file
		BufferedReader in = null;
		ArrayList<String> file = new ArrayList<>();
		try {
			in = new BufferedReader(new FileReader("data/emails.csv"));
			String line;
			while ((line = in.readLine()) != null) {
				file.add(line);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		int nulls = 0;
		for (int i = 0; i < file.size(); i++) {
			if (i < 100 || i > file.size()-100) {
				nulls = (file.get(i).split(",")[2].equals("null")) ? nulls+1 : nulls;
			}
		}
		
		System.out.println("Number of files: " + file.size() + "\tNumber of nulls: " + nulls);

	}

	private static void onlyEmails() {
		// Open file
		BufferedReader in = null;
		ArrayList<String> file = new ArrayList<>();
		try {
			in = new BufferedReader(new FileReader("data/emails.csv"));
			String line;
			while ((line = in.readLine()) != null) {
				file.add(line);
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Open file
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("data/emailsonly.csv"));
			for(String line: file) {
				out.write(line.split(",")[0]);
				out.write("\n");
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		//getEmailList();
		//update();
		printLargeCsv();
		//onlyEmails();
	}

}
