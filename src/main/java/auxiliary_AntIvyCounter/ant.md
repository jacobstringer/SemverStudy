The ant project checks which ant files use ivy. 

It assumes that the structure of the Ant build scripts are in path / first character of the project name /  build.xml file. Adjust the path string at the start of the file accordingly.

The output is an IvyCounts.csv of the project url and a 1 if it uses ivy or 0 if it doesn't.