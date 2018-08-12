import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ZipSize {

	public static void main(String[] args) {
        // CHANGE THESE FOUR VARIABLES ONLY
        String[] ziparray = new String[]{"E:/Build Scripts/gradlewithsubmodules.zip"};
        
		for (String zip: ziparray) {
            long count = 0;
			try (ZipFile zf = new java.util.zip.ZipFile(zip)) {
				for (Enumeration<? extends ZipEntry> entries = zf.entries(); entries.hasMoreElements();) {				
					// Read in file and place on queue
                    try {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory())
                            continue;
                        
                        count++;
                    } catch (IllegalArgumentException e1) {
                        e1.printStackTrace();
                    }
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
            
            System.out.println(zip + "\t" + count);
		}
	}
}
