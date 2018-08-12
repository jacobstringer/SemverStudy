This project takes existing files and queries metadata for them.

The files inputted are assumed to be in zip folders, which can be adjusted in the producer file.

The type of metadata gathered can be adjusted by using different consumers. Switch the consumer used in production to gather different data.

ConsumerGetCommitInfo collects metadata from GitHub about last commits and inputs the latest commit to the DB

ConsumerGetSaveCommitJSON collects the commit files and saves them to the file system.


