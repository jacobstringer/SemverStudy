import os

count = 0
for root, dirs, files in os.walk("D://Build Scripts/package"):
    count += len(files)

print(count)