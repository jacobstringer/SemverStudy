import os
import os.path

for name in ["pom", "package", "rake"]:
    source = "D:\\Build Scripts\\" + name
    dst = "D:\\Build Scripts\\" + name + "\\" + name + ".csv"
    
    out = open(dst, "w")
    
    counter = 0
    total = 0
    for root, dirs, files in os.walk(source):
        for file in files:
            out.write(file.replace(',', '!!!') + "," + str(os.path.getsize(os.path.join(root, file))) + '\n')
            counter += 1
            if counter >> 16:
                total += 2**16
                print (total, 'files read')
                counter = 0
                
    print(total + counter)            
    out.close()