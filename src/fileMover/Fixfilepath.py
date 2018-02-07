import os
import os.path
import shutil

source = "D:\\Build Scripts"
dst = "D:\\Build Scripts"
#os.mkdir("D:\\Build Scripts\\rake")

for root, dirs, files in os.walk(source):
    if [x for x in root.split('\\') if x in ['build', 'gradle', 'pom', 'rake', 'package']]:
        #print(root)
        continue
    for file in files:
        info = file.split('+')[-1].lower()
        
        folder = ''
        if info == 'build.xml':
            folder = 'build'
        elif info == 'build.gradle':
            folder = 'gradle'
        elif info == 'pom.xml':
            folder = 'pom'
        elif info == 'package.json':
            folder = 'package'
        elif info == 'Rakefile':
            folder = 'rake'
        
        if folder:
            shutil.move(os.path.join(root, file), os.path.join(dst, folder, file[0], file))
        else:
            print(info, file)
