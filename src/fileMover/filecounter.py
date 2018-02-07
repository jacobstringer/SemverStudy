import os

count = 0
for root, dirs, files in os.walk("C:\\Users\\Jacob\\Desktop\\MasseyReadings\\Java\\_2018SS\\githubSamples"):
    count += len(files)

print(count)