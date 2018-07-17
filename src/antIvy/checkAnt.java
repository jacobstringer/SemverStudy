package antIvy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class checkAnt {
	
	// Creates a csv file from ant files, checking which ones have Ivy references

	public static void main(String[] args) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader("D://Build Scripts/Build/buildrecoveredfiles.csv"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter("data/IvyInfo2.csv"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		String temp = "";
		
		while(true) {
			// Get one observation at a time out of the database file
			String[] info = null;
			try {
				temp = in.readLine();
				if (temp == null) {
					break;
				}
				info = temp.split(",");
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Change url to file name as saved on computer
			String file = info[0].replaceFirst("https://github.com/", "").replace('/', '+');

			BufferedReader in2;
			try {
				in2 = new BufferedReader(
						new FileReader("D:\\Build Scripts\\Build\\" + file.charAt(0) + "\\" + file + "+build.xml"));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				continue;
			}

			// Read in file into a single string
			StringBuilder temper = new StringBuilder();
			String tempString = null;
			while(true) {
				try {tempString = in2.readLine();} catch (IOException e) {e.printStackTrace();}
				if (tempString == null) {
					break;
				} else {
					temper.append(tempString);
				}
			}

			if (temper.toString().contains("ivy")) {
				out.write(file+"+build.xml,1\n");
			} else {
				out.write(file+"+build.xml,0\n");
			}


			// Close connection
			try {in2.close();} catch (IOException e) {e.printStackTrace();}
		}
		// Close connection
		try {in.close();} catch (IOException e) {e.printStackTrace();}
		try {out.close();} catch (IOException e) {e.printStackTrace();}

	}

}
