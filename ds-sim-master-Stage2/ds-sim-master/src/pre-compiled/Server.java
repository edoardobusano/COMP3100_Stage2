
// Simple class that contains the information wee need to schedule jobs after
// type and the number of cores are the two important attributes

public class Server {

	public int id;
	public String type;
	public int cores;

	Server(int id, String t, int c) {
		this.id = id;
		this.type = t;
		this.cores = c;
	}
}