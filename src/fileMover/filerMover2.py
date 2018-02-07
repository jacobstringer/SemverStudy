import os
import os.path
import shutil

source = "C:\\Users\\Jacob\\Desktop\\MasseyReadings\\Java\\_2018SS\\githubSamples"
dst = "D:\\Build Scripts"
#os.mkdir("D:\\Build Scripts\\rake")

counter = 0
for root, dirs, files in os.walk(source):
    for file in files:
        if not os.path.exists(os.path.join(dst, root.split('\\')[-1], file[0])):
            os.mkdir(os.path.join(dst, root.split('\\')[-1], file[0]))
        #print(os.path.join(root, file), os.path.join(dst, file[0], file))
        try:
            shutil.move(os.path.join(root, file), os.path.join(dst, root.split('\\')[-1], file[0], file))
            counter += 1
            if counter % 1000 == 0:
                print(counter, "files moved")
        except Exception as e:
            print(e)
