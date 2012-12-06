package server;

import java.io.*;
import java.net.*;

/**
 * This class creates an HTTP web server which will host an HttpHandler to parse and handle URLs
 * passed from the client to the server.
 * @author Eric
 * Requires HttpHandler
 */
public class HttpServer extends Thread{
	int port;
	ServerSocket svr = null;

	/**
	 * Creates an HttpServer instance required for threading
	 */
	public HttpServer() {
	}

	/**
	 * Static entry point for program
	 * @param a
	 */
	public static void main(String a[]) {
		new HttpServer().run();
	}

	/**
	 * Searches for a port on the current system that is not in use and creates a SeverSocket bound
	 * to this port for the HttpServer and HttpHandler to use.
	 */
	public void run() {
		for(int i = 3000; i < 65000; i++) {
			try {
				svr = new ServerSocket(i);
				port = i;
				System.out.println("Running on port: " + port);
				break;
			} catch (IOException err) {
			}
		}
		while (true) {
			try {
				Socket client = svr.accept();
				new HttpHandler(client).start();
			} catch (IOException err) {}
		}
	}
}

