CREATE TABLE dependencies (
	url varchar(200) PRIMARY KEY,
	lang1 varchar(100),
	lang2 varchar(100),
	buildtype varchar(50),
	users varchar(100),
	ghid int
);