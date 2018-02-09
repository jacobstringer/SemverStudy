%LET build = D:\Build Scripts;
%LET dir = C:\Users\Jacob\Desktop\MasseyReadings\Java\_2018SS\data;


****** EXPLORATORY ANALYSIS OF FILE NUMBERS AND SIZES;
*** Record in the data;
DATA ant;
	INFILE "&build\build\build2.csv" dlm=',';
	INPUT file $ size;
	type = 'ant';
RUN;

DATA gradle;
	INFILE "&build\gradle\gradle2.csv" dlm=',';
	INPUT file $ size;
	type = 'gradle';
RUN;

DATA pom;
	INFILE "&build\pom\pom2.csv" dlm=',';
	INPUT file $ size;
	type = 'pom';
RUN;

DATA package;
	INFILE "&build\package\package2.csv" dlm=',';
	INPUT file $ size;
	type = 'package';
RUN;

DATA rake;
	INFILE "&build\rake\rake2.csv" dlm=',';
	INPUT file $ size;
	type = 'rake';
RUN;

* Combines all the datasets together;
DATA combo;
	SET package gradle pom ant rake;
RUN;

* Gather introductory information about the datasets;
ODS HTML;
PROC MEANS data=combo MEAN STDDEV P5 MEDIAN P95;
	CLASS type;
	VAR size;
RUN;
ODS HTML CLOSE;

* Looks at the univariate distribution of the ln(size) - Package is too large for this function and runs out of memory;
%MACRO UNI(dat=);
DATA temp;
	SET &dat;
	ln = log(size);
RUN;

PROC UNIVARIATE data=temp PLOT;
	VAR ln;
	TITLE "&dat";
RUN;
%MEND UNI;

%UNI(dat=rake);
%UNI(dat=ant);
%UNI(dat=gradle);
%UNI(dat=pom);





****** IVY ANT
Info about Ant projects that use Ivy;
DATA ivy;
	INFILE "&dir\IvyInfo.csv" DLM=',';
	INPUT project $ ivy;
DATA ivy2;
	INFILE "&dir\IvyInfo2.csv" DLM=',';
	INPUT project $ ivy;
DATA ivycombo;
	SET ivy ivy2;
RUN;

ODS HTML;
PROC MEANS data=ivycombo N  MEAN STD SUM;
	VAR ivy;
RUN;
ODS HTML CLOSE;



******* NPM;
DATA npm;
	INFILE "&dir\npm.csv" DSD;
	INPUT url $ norange micro minor major microrange minorrange majorrange microsimp minorsimp
	    microcomp minorcomp majorcomp microlt minorlt majorlt microltsimp minorltsimp gt urldep
	    git filedep unknowndep norangedev microdev minordev majordev microrangedev minorrangedev
	    majorrangedev  microsimpdev  minorsimpdev microcompdev minorcompdev majorcompdev microltdev
	    minorltdev majorltdev microltsimpdev minorltsimpdev gtdev urldepdev gitdev filedepdev
	    unknowndepdev notjson $;
		if notjson = 't' then notjsonnum = 1; else notjsonnum = 0;
RUN;

ODS HTML;
PROC MEANS data=npm MAXDEC=3 N MEAN STD P95 P99 MAX;
RUN;
ODS HTML CLOSE;



******* FULL FILE OF STATS;
* General stats;

