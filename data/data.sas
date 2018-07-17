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

DATA npm_condensed;
	SET npm;
	fixed = SUM(norange, norangedev, urldep, urldepdev, git, gitdev, filedep, filedepdev);
	micro = SUM(micro, microrange, microsimp, microcomp, microlt, microltsimp, microdev, microrangedev, microsimpdev, microcompdev, microltdev, microltsimpdev);
	minor = SUM(minor, minorrange, minorsimp, minorcomp, minorlt, minorltsimp, minordev, minorrangedev, minorsimpdev, minorcompdev, minorltdev, minorltsimpdev);
	major = SUM(major, majorrange, majorcomp, majorlt, gt, majordev, majorrangedev, majorcompdev, majorltdev);
	variable = SUM(micro, minor, major);
	total = SUM(fixed, variable);
	KEEP fixed variable total micro minor major unknowndep;
RUN;

PROC UNIVARIATE data=npm_condensed;
RUN;

%MACRO npmbytype (type=);
DATA t;
	SET npm_condensed;
	IF &type > 0;
PROC UNIVARIATE data=t;
	var &type;
RUN;
%MEND npmbytype;

%npmbytype(type=fixed);
%npmbytype(type=micro);
%npmbytype(type=minor);
%npmbytype(type=major);
%npmbytype(type=variable);
%npmbytype(type=total);
	
DATA npmmixed;
	SET npm_condensed;
	IF micro > 0 and minor > 0 or micro > 0 and major > 0 or minor > 0 and major > 0;
PROC UNIVARIATE data=npmmixed;
	var variable;
RUN;

DATA npmnofixed;
	SET npm_condensed;
	IF fixed = 0;
PROC UNIVARIATE data=npmnofixed;
RUN;



******* FULL FILE OF STATS;
* General stats;
DATA total;
	INFILE "&dir\dependencies.csv" DSD;
	INPUT url $ lang1 $ lang2 $ buildtype $ sem_dep other_dep users $ ghid;
RUN;

ODS HTML;
PROC MEANS data=total;
	CLASS lang1 lang2 buildtype;
	OUTPUT out=buildresults N=N;
RUN;

DATA buildresults2;
	SET buildresults;
	IF N > 10000;
	IF NOT missing(lang1) AND NOT missing(buildtype);
PROC PRINT data=buildresults2;
RUN;

PROC CONTENTS data=buildresults;
RUN;
ODS HTML CLOSE; 

DATA total2;
	SET total;
	IF buildtype > .;
RUN;



******** GRADLE;
DATA gradle;
	INFILE "&dir\gradlefiles.csv" DSD;
	INPUT url $ lines fixed micro minor major range files methods;
RUN;

DATA gradleonlydeps;
	SET gradle;
	IF lines > 0;
RUN;

PROC MEANS data=gradle;*onlydeps;
RUN;

PROC UNIVARIATE data=gradleonlydeps PLOT;
RUN;



********* GRADLE WITH SUBFILES;
DATA gradlesubs;
	INFILE "&dir\gradlefileswithsub.csv" DSD;
	INPUT url $ fixed micro minor major range lines files methods subfiles;
	fix = SUM(fixed, files);
	var = SUM(micro, minor, major, range);
	total = SUM(fix, var);
DATA gradlesubsdeps;
	SET gradlesubs;
	IF total > 0;
DATA gradlesubfix;
	SET gradlesubsdeps;
	IF fix > 0;
DATA gradlesubmicro;
	SET gradlesubsdeps;
	IF micro > 0;
DATA gradlesubminor;
	SET gradlesubsdeps;
	IF minor > 0;
DATA gradlesubmajor;
	SET gradlesubsdeps;
	IF major > 0;
DATA gradlesubmixedvars;
	SET gradlesubsdeps;
	IF micro > 0 and minor > 0 or micro > 0 and major > 0 or minor > 0 and major > 0;
DATA gradlesubvar;
	SET gradlesubsdeps;
	IF var > 0;
DATA gradlenofix;
	SET gradlesubsdeps;
	IF fix = 0;
RUN;
PROC UNIVARIATE data=gradlesubs;
RUN;
PROC UNIVARIATE data=gradlesubfix;
	VAR fix;
PROC UNIVARIATE data=gradlesubmicro;
	VAR micro;
PROC UNIVARIATE data=gradlesubminor;
	VAR minor;
PROC UNIVARIATE data=gradlesubmajor;
	VAR major;
PROC UNIVARIATE data=gradlesubmixedvars;
	var var;
PROC UNIVARIATE data=gradlesubvar;
	VAR var;
PROC UNIVARIATE data=gradlesubsdeps;
	VAR total;
RUN;





******** POM;
DATA pom;
	INFILE "&dir\pom.csv" DSD;
	INPUT url $ fixed micro minor major other;
RUN;

DATA pom2;
	SET pom;
	total = sum(fixed, micro, minor, major, other);
	if total > 0;
RUN;

DATA pommicro;
	SET pom;
	if micro > 0;
RUN;

DATA pomminor;
	SET pom;
	if minor > 0;
RUN;

DATA pommajor;
	SET pom;
	if major > 0;
RUN;

DATA pomfixed;
	SET pom;
	if fixed > 0;
RUN;

DATA mixed;
	SET pom;
	IF major > 0 and micro > 0 or major > 0 and minor > 0 or micro > 0 and minor > 0;
RUN;

DATA pomvariable;
	SET pom;
	IF micro > 0 or minor > 0 or major > 0;
RUN;

DATA pomnofixed;
	SET pom2;
	IF fixed = 0;
RUN;

PROC UNIVARIATE data=pomfixed;
	VAR fixed;
Run;

ODS HTML;
PROC UNIVARIATE data=pommicro;
	VAR micro;
PROC UNIVARIATE data=pomminor;
	VAR minor;
PROC UNIVARIATE data=pommajor;
	VAR major;
RUN;

PROC UNIVARIATE data=pom2;
RUN;
