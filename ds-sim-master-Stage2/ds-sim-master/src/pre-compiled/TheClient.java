import java.net.*;
import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
//DataInputStream
//DataOutputStrem

// ./ds-server -c <CONFIG> -n

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
		System.out.println("Sent HELLO");
		inputString = read();
		System.out.println("Received " + inputString);
		write("AUTH " + System.getProperty("user.name"));
		System.out.println("Sent Auth " + System.getProperty("user.name"));
		inputString = read();
		System.out.println("Received " + inputString);
		File file = new File("ds-system.xml");
		readFile(file);
		write("REDY");
		System.out.println("Sent REDY");
		inputString = read();
		System.out.println("Received " + inputString);
		allToLargest();
		quit();
	}

	// allToLargest deals with the communication between server and client after the
	// client sent the first REDY, and
	// the server sent the first reply. If the reply is NONE, the method will just
	// quit and the connection will be closed,
	// while, if the client receives other commands, the algorithm will run as
	// expected. It follows the workflow shown in
	// the ds-server pdf file provided, so the algorithm loops until it receives a
	// NONE as a reply from the server.
	// In case it does not receive a JOBN reply, the client will write REDY, so that
	// the command can be skipped.
	// When the server sends a JOBN message, the message is split and data about the
	// job is gathered.
	// The server type needed in the SCHD command is found thanks to the readFile
	// method and the findLargestServer method.
	public void allToLargest() {
		if (inputString.equals("NONE")) {
			quit();
		} else {
			while (!completed) {
				if (inputString.equals("OK") || inputString.equals(".") || inputString.equals(".OK")) {
					write("REDY");
					System.out.println("Sent REDY");
					inputString = read();
					System.out.println("Received " + inputString);
				}
				String[] splitMessage = inputString.split("\\s+");
				String firstWord = splitMessage[0];
				while (firstWord.equals("JCPL") || firstWord.equals("RESF") || firstWord.equals("RESR")) {
					write("REDY");
					System.out.println("Sent REDY");
					inputString = read();
					System.out.println("Received " + inputString);

					splitMessage = inputString.split("\\s+");
					firstWord = splitMessage[0];
				}
				if (firstWord.equals("NONE")) {
					completed = true;
					break;
				}

				String[] jobSections = inputString.split("\\s+");

				write("GETS Capable " + jobSections[4] + " " + jobSections[5] + " " + jobSections[6]);
				String dataString = read();
				System.out.println("Received " + dataString);
				write("OK");
				dataString = read();
				System.out.println("Received " + dataString);
				write("OK");
				String[] lines = dataString.split("\\r?\\n");
				String[] sections = lines[0].split("\\s+");

				String num = jobSections[2];
				String scheduleMessage = "SCHD " + num + " " + sections[0] + " " + sections[1];
				write(scheduleMessage);
				System.out.println("JOB SENT: SCHD " + num + " " + sections[0] + " " + sections[1]);
				// String[] jobSections = inputString.split("\\s+");
				// String num = jobSections[2];
				// String scheduleMessage = "SCHD " + num + " " +
				// servers[largestServerIndex].type + " " + "0";
				// write(scheduleMessage);
				// System.out.println("JOB SENT: SCHD " + num + " " +
				// servers[largestServerIndex].type + " " + "0");
				inputString = read();
				System.out.println("Received " + inputString);
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
			out.write((text).getBytes());
			// System.out.print("SENT: " + text);
			out.flush();
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
	}

	public String read() {
		String text = "";
		try {
			while (!in.ready()) { // do nothing if in is not ready
			}
			while (in.ready()) { // if it is then return the read char.
				text += (char) in.read();
			}
			// text = in.readLine();
			// System.out.print("RCVD: " + text);
			inputString = text;
		} catch (IOException i) {
			System.out.println("ERR: " + i);
		}
		return text;
	}

	public void quit() {
		try {
			write("QUIT");
			System.out.println("Sent QUIT");
			inputString = read();
			System.out.println("Received" + inputString);
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