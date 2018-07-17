# SemverStudy
Surveys how much version ranges are used in automatic build technologies

## Info
/src/githubScraper contains the logic for scraping Github for build scripts. Run ProductionGitHub after setting the GitHub tokens (for additional accesses per hour) and setting up the database (information is in the file, and run the sql file before beginning).

/src/dependencyFinders contains the logic for classifying information. It assumes that the files coming in will be zipped.

The other folders and files are auxiliary scripts and files.

## Statistics
http://www.jacobstringer.co.nz/Results.html

## Completed Datasets
https://drive.google.com/drive/folders/1LGghgE7mtCSLi7CwG_KNsP_9Nm1fNWNF?usp=sharing

## Summary Statistics
Build Type | Scripts Found | Scripts with Dependencies | Classified
---|---|---|---
NPM | 8100k | 7500k | 
Gradle | 2400k | 300k | 300k
Maven | 2300k | 1896k | 1896k (31k have major, 4.3k have minor, 3.7k have micro ranges)
Rake | 1000k | | 
Ant | 500k | | 

