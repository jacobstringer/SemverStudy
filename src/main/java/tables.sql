CREATE TABLE dependencies (
	url varchar(200) PRIMARY KEY,
	lang1 varchar(100),
	lang2 varchar(100),
	buildtype varchar(50),
	users varchar(100),
	ghid int, 
	submodules int,
	fork bool,
	latestcommit date,
	firstcommit date,
	numcommitters int
);

CREATE TABLE pomnew (
      url varchar(200) PRIMARY KEY,
      soft int,
      fixed int,
  	micro int,
  	minor int,
  	major int,
  	other int
  );