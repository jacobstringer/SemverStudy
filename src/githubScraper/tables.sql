-- Gradle tables
CREATE TABLE gradleentries (
	url varchar(200) PRIMARY KEY,
	command varchar(50),
	versiontype varchar(30),
	raw varchar(200)
);

CREATE TABLE gradleentrieswithsub (
	url varchar(200) PRIMARY KEY,
	command varchar(50),
	versiontype varchar(30),
	raw varchar(200)
);

CREATE TABLE gradlefiles (
	url varchar(200) PRIMARY KEY,
	lines int,
	fixed int,
	micro int,
	minor int,
	major int,
	nrange int,
	files int,
	methods int,
);

CREATE TABLE gradlefileswithsub (
	url varchar(200) PRIMARY KEY,
	lines int,
	fixed int,
	micro int,
	minor int,
	major int,
	nrange int,
	files int,
	methods int,
	subfiles int,
);



-- NPM Tables
CREATE TABLE npm (
	url varchar(200) PRIMARY KEY,
	norange integer,
    micro integer,
    minor integer,
    major integer,
    microrange integer,
    minorrange integer,
    majorrange integer,
    microsimp integer,
    minorsimp integer,
    microcomp integer,
    minorcomp integer,
    majorcomp integer,
    microlt integer,
    minorlt integer,
    majorlt integer,
    microltsimp integer,
    minorltsimp integer,
    gt integer,
    urldep integer,
    git integer,
    filedep integer,
    unknowndep integer,
    norangedev integer,
    microdev integer,
    minordev integer,
    majordev integer,
    microrangedev integer,
    minorrangedev integer,
    majorrangedev integer,
    microsimpdev integer,
    minorsimpdev integer,
    microcompdev integer,
    minorcompdev integer,
    majorcompdev integer,
    microltdev integer,
    minorltdev integer,
    majorltdev integer,
    microltsimpdev integer,
    minorltsimpdev integer,
    gtdev integer,
    urldepdev integer,
    gitdev integer,
    filedepdev integer,
    unknowndepdev integer,
    notjson boolean,
);

CREATE TABLE npmcomposite (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    micro boolean,
    minor boolean,
    major boolean,
    CONSTRAINT npmcomposite_pkey PRIMARY KEY (url, dep)
);

CREATE TABLE npmfile (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    CONSTRAINT npmfile_pkey PRIMARY KEY (url, dep)
);

CREATE TABLE npmgit (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    CONSTRAINT npmgit_pkey PRIMARY KEY (url, dep)
);

CREATE TABLE npmrange (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    micro boolean,
    minor boolean,
    major boolean,
    CONSTRAINT npmrange_pkey PRIMARY KEY (url, dep)
);

CREATE TABLE npmunmatched (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    CONSTRAINT npmunmatched_pkey PRIMARY KEY (url, dep)
);

CREATE TABLE npmurl (
    url varchar(200) NOT NULL,
    dep varchar(200) NOT NULL,
    CONSTRAINT npmurl_pkey PRIMARY KEY (url, dep)
);



-- Maven tables
CREATE TABLE pom (
    url character varying(200) PRIMARY KEY,
    fixed integer,
    micro integer,
    minor integer,
    major integer,
    other integer,
);

