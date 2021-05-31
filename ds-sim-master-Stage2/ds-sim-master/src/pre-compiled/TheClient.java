import java.net.*;
import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
// Use ./test-results "java TheClient" -o ru -n -c ../../configs/other/

public class TheClient {

	// Create global variables for the socket, and the input and output, which we
	// will need to write and read messages
	// between server and client
	private Socket socket = null;
	private BufferedReader in = null;
	private DataOutputStream out = null;
	private Server[] servers = new Server[1];
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
		// Start communication with server
		write("HELO");
		// Read server reply
		inputString = read();
		// Authenticate ourselves using our user name
		write("AUTH " + System.getProperty("user.name"));
		// Read server reply
		inputString = read();
		// Create a new file to parse data from the server about the system
		File file = new File("ds-system.xml");
		readFile(file);
		// Send a message to state the client is ready to receive job info
		write("REDY");
		// Read server reply
		inputString = read();
		// Use the algorithm to make scheduling decision
		theAlgorithm();
		// Quit and stop connection with the server
		quit();
	}

	// Improved algorithm for scheduling jobs. It currently checks if there are available servers for the job that needs
	// to be scheduled, and it sends it to the first available server with the highest core count.
	// If there are no available servers, the job is scheduled to the biggest server
	public void theAlgorithm() {
		// If the server has nothing to send, the algorithm is skipped
		if (inputString.equals("NONE")) {
			quit();
		} else {
			// Within this loop, the client first checks which message is sent by the server, and provides
			// a reply following the client-server protocol
			while (!completed) {
				// If the received message is "OK" or ".", it means that our client is ready to receive more data
				// from the server, and the server has finished delivering the previous information
				if (inputString.equals("OK") || inputString.equals(".") || inputString.equals(".OK")) {
					write("REDY");
					inputString = read();
				}
				// Within this loop, we simply skip the messages from the server that update us with information about
				// jobs already scheduled
				String[] splitMessage = inputString.split("\\s+");
				String firstWord = splitMessage[0];
				while (firstWord.equals("JCPL") || firstWord.equals("RESF") || firstWord.equals("RESR")) {
					write("REDY");
					inputString = read();

					splitMessage = inputString.split("\\s+");
					firstWord = splitMessage[0];
				}
				// If the server has nothing else to send, we end the loop
				if (firstWord.equals("NONE")) {
					completed = true;
					break;
				}
				// The client reads the last message from the server, which is job information that we will need for our
				// scheduling
				String[] jobSections = inputString.split("\\s+");
				// The client will now select the perfect server for the job to run, following the algorithm logic
				selectServer(jobSections);
				inputString = read();
				String num = jobSections[2];
				// The job is finally scheduled using the server picked from selectServer(jobSections);
				String scheduleMessage = "SCHD " + num + " " + sections[0] + " " + sections[1];
				write(scheduleMessage);
				inputString = read();
			}
		}
	}
	
	// This function takes in the job details in the form of an array of strings, and decides which server is the best fitting
	// Following the algorithm logic. It first checks the Available servers, and picks the one with the highest core count that
	// is able to run the job.
	// In the case there are no Available servers, it search for the server with the highest core count that is able to run
	// the job. "sections" is the array of strings that contain the server details of the server that will be picked at the end.
	public void selectServer(String[] j){
				write("GETS Avail " + j[4] + " " + j[5] + " " + j[6]);
				String dataString = read();
				String[] dataLines = dataString.split("\\s+");
				int linesNum = Integer.parseInt(dataLines[1]);
				write("OK");
				dataString = read();
				if (!dataString.equals(".")) {
					String[] lines = dataString.split("\\r?\\n");
					sections = lines[0].split("\\s+");
					compareServers(dataString, linesNum, lines);
				} else {
					write("GETS Capable " + j[4] + " " + j[5] + " " + j[6]);
					dataString = read();
					dataLines = dataString.split("\\s+");
					linesNum = Integer.parseInt(dataLines[1]);
					write("OK");
					dataString = read();
					String[] lines = dataString.split("\\r?\\n");
					sections = lines[0].split("\\s+");
					compareServers(dataString, linesNum, lines);
				}
	}

	// This function is run within selectServer, and it is the specific code that looks for the server with the highest core
	// count, saving the data of the selected server into "sections".
	public void compareServers(String ds, int ln, String[] l){
		for (int a = 0; a < ln; a++){

			l = ds.split("\\r?\\n");
			String[] sec = l[0].split("\\s+");
			if (Integer.parseInt(sec[4]) > Integer.parseInt(sections[4])){
				sections = sec;
			}
			
			if (a == ln -1){
				write("OK");
				break;
			}
			else {
				ds = read();
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
		} catch (Exception i) {
			i.printStackTrace();
		}

	}
	// Function that receive a String containing the message to be sent, and delivers it to the server
	public void write(String text) {
		try {
			out.write((text + "\n").getBytes());
			out.flush();
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
	}

	// Function that reads the message sent by the server and returns it as a String
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

	// Function that sends the message QUIT to end the connection with the server, and checks wether the message returned is
	// QUIT as well, terminating all processes following the protocol
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