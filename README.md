NotGPT is a basic search engine built in Java. It collects information from web pages, stores it locally, and allows users to search through the collected content using keyword-based queries. The project uses jsoup (version 1.8.3) to parse HTML content.

How it works:
	•	The Collect class crawls and downloads web pages using BetterBrowser and jsoup.
	•	InfoFile and HardDisk handle saving and retrieving page data to and from local storage.
	•	Search and Rank process user queries and rank results based on keyword relevance.
	•	NotGPT.java is the main class that starts the search interface and handles user input.

To use this project:
	1.	Make sure you have Java 8 or higher installed.
	2.	Compile all Java files using the following command:
javac -cp .:jsoup-1.8.3.jar *.java
(On Windows, use ; instead of : in the classpath)
	3.	Run the program using:
java -cp .:jsoup-1.8.3.jar NotGPT

The interface will prompt you to enter a search query. After you enter a keyword, it will return ranked results based on the content previously collected by the crawler.
