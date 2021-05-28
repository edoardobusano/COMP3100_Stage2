import java.net.*;
import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
// ./ds-server -c <CONFIG> -n

// Use ./test-results "java TheClient" -o ru -n -c ../../configs/other/

public class TheClient {

	// Create global variables for the socket, and the input and output, which we
	// will need to write and read messages
	// between server and client
	private Socket socket = null;
	private BufferedReader in = null;
	private DataOutputStream out = null;
	private Server[] servers = new Server[1];
	private int largestServerIndex = 0;
	private String inputString;
	private Boolean completed = false;
	private String[] sections;

	// Constructor for our client class: we connect the socket to the address
	// 127.0.0.1 and to the port 50000, as
	// provided by the server, and we initialize the input variable (in) and the
	// output (out)
	// in will be used for reading the messages sent by the server, while out will
	// be used for writing messages
	// to the server
	public TheClient() {
		try {
			socket = new Socket("localhost", 50000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new DataOutputStream(socket.getOutputStream());
		} catch (UnknownHostException i) {
			System.out.println("Error: " + i);
		} catch (IOException i) {
			System.out.println("Error: " + i);
		}
	}

	// We instantiate one client object in main, and we need some method that will
	// start communication with the server,
	// which I called start().
	public static void main(String args[]) {
		TheClient client = new TheClient();
		client.start();
	}

	public void start() {
		write("HELO");
		inputString = read();
		write("AUTH " + System.getProperty("user.name"));
		inputString = read();
		File file = new File("ds-system.xml");
		readFile(file);
		write("REDY");
		inputString = read();
		theAlgorithm();
		quit();
	}

	public void theAlgorithm() {
		if (inputString.equals("NONE")) {
			quit();
		} else {
			while (!completed) {
				if (inputString.equals("OK") || inputString.equals(".") || inputString.equals(".OK")) {
					write("REDY");
					inputString = read();
				}
				String[] splitMessage = inputString.split("\\s+");
				String firstWord = splitMessage[0];
				while (firstWord.equals("JCPL") || firstWord.equals("RESF") || firstWord.equals("RESR")) {
					write("REDY");
					inputString = read();

					splitMessage = inputString.split("\\s+");
					firstWord = splitMessage[0];
				}
				if (firstWord.equals("NONE")) {
					completed = true;
					break;
				}

				String[] jobSections = inputString.split("\\s+");

				write("GETS Avail " + jobSections[4] + " " + jobSections[5] + " " + jobSections[6]);
				String dataString = read();
				String[] dataLines = dataString.split("\\s+");
				int linesNum = Integer.parseInt(dataLines[1]);
				write("OK");
				dataString = read();
				if (!dataString.equals(".")) {
					String[] lines = dataString.split("\\r?\\n");
					sections = lines[0].split("\\s+");
					for (int a = 0; a < linesNum; a++){

						lines = dataString.split("\\r?\\n");
						String[] sec = lines[0].split("\\s+");
						if (Integer.parseInt(sec[4]) > Integer.parseInt(sections[4])){
							sections = sec;
						}
						
						if (a == linesNum -1){
							write("OK");
							break;
						}
						else {
							dataString = read();
						}
					}
					
				} else {
					write("GETS Capable " + jobSections[4] + " " + jobSections[5] + " " + jobSections[6]);
					dataString = read();
					dataLines = dataString.split("\\s+");
					linesNum = Integer.parseInt(dataLines[1]);
					write("OK");
					dataString = read();
					String[] lines = dataString.split("\\r?\\n");
					sections = lines[0].split("\\s+");
					for (int a = 0; a < linesNum; a++){

						lines = dataString.split("\\r?\\n");
						String[] sec = lines[0].split("\\s+");
						if (Integer.parseInt(sec[4]) > Integer.parseInt(sections[4])){
							sections = sec;
						}
						
						if (a == linesNum -1){
							write("OK");
							break;
						}
						else {
							dataString = read();
						}
					}
				}
				inputString = read();

				String num = jobSections[2];
				String scheduleMessage = "SCHD " + num + " " + sections[0] + " " + sections[1];
				write(scheduleMessage);
				inputString = read();
			}
		}
	}

	// This method parses through the XML file found at the path stated in the
	// start() method.
	// It iterates through the file looks for attributes found in the XML file.
	// It then stores those values in an array
	public void readFile(File file) {
		try {

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document systemDocument = builder.parse(file);
			systemDocument.getDocumentElement().normalize();

			NodeList serverNodeList = systemDocument.getElementsByTagName("server");
			servers = new Server[serverNodeList.getLength()];
			for (int i = 0; i < serverNodeList.getLength(); i++) {
				Element server = (Element) serverNodeList.item(i);
				String t = server.getAttribute("type");
				int c = Integer.parseInt(server.getAttribute("coreCount"));
				Server temp = new Server(i, t, c);
				servers[i] = temp;
			}
			largestServerIndex = findLargestServer();
		} catch (Exception i) {
			i.printStackTrace();
		}

	}

	// Returns the index of the largest server(CPU cores) in the array
	// created by the readFile() method
	public int findLargestServer() {
		int largestServer = servers[0].id;
		for (int i = 0; i < servers.length; i++) {
			if (servers[i].cores > servers[largestServer].cores) {
				largestServer = servers[i].id;
			}
		}
		return largestServer;
	}

	public void write(String text) {
		try {
			out.write((text + "\n").getBytes());
			out.flush();
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
	}

	public String read() {
		String text = "";
		try {
            text = in.readLine();
			inputString = text;
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
		return text;
	}

	public void quit() {
		try {
			write("QUIT");
			inputString = read();
			if (inputString.equals("QUIT")) {
				in.close();
				out.close();
				socket.close();
			}
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
	}

}