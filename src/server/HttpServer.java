package server;

import java.io.*;
import java.net.*;

public class HttpServer {
	int port;
	int cnt;
	ServerSocket svr = null;

	public HttpServer() {
	}

	public static void main(String a[]) {
		//try {
		//	port = Integer.parseInt(a[0]);
		//} catch (Exception err) {
		//	System.err.println("Usage: <port>");
		//	System.exit(-1);
		//}

		new HttpServer().run();
	}

	public void run() {

		for(int i = 3000; i < 65000; i++) {
			try {
				svr = new ServerSocket(i);
				port = i;
				System.out.println("Running on port: " + port);
				break;
			} catch (IOException err) {
				System.err.println("Socket invalid or in use");
				System.exit(-1);
			}
		}

		while (true) {
			try {
				Socket client = svr.accept();
				cnt++;
				new HttpHandler(client,cnt).start();
			} catch (IOException err) {}
		}
	}
}

