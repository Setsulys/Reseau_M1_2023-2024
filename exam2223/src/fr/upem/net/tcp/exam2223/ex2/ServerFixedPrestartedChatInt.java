package fr.upem.net.tcp.exam2223.ex2;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.plaf.SliderUI;

public class ServerFixedPrestartedChatInt {
	private static final Logger logger = Logger.getLogger(fr.upem.net.tcp.exam2223.ex2.ServerFixedPrestartedChatInt.class.getName());
	private final ServerSocketChannel serverSocketChannel;
	private final int nbClients;
	private final Semaphore semaphore;
	private final ByteBuffer buffer = ByteBuffer.allocateDirect(Integer.BYTES);
	private final ArrayList<SocketChannel> channels = new ArrayList<>();

	public ServerFixedPrestartedChatInt(int port, int nbClients) throws IOException {
		this.serverSocketChannel = ServerSocketChannel.open();
		this.nbClients = nbClients;
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName()
				+ " starts on port " + port);
		semaphore= new Semaphore(nbClients);
	}

	private void serve(SocketChannel sc)throws IOException{
		for(;;) {
			if(!readFully(sc,buffer)) {
				logger.info("Not readfully");
				return;
			}
			for(var e : channels) {
				e.write(buffer.flip());
			}
			buffer.clear();
		}
	}

	static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining()) {
			if (sc.read(buffer) == -1) {
				logger.info("Input stream closed");
				return false;
			}
		}
		return true;
	}

	public void worker(){
		while(!Thread.interrupted()) {
			try {
				semaphore.acquire();
				SocketChannel client  = serverSocketChannel.accept();
				channels.add(client);
				try {
					serve(client);
				}finally {
					channels.removeIf(e-> e.equals(client));
					semaphore.release();
					silentlyClose(client);
				}
			}catch(InterruptedException e ) {
				logger.info("Channel closed");
			}catch(IOException e) {
				logger.log(Level.SEVERE,"IOException",e);
			}

		}
	}

	public void launch() {
		// TODO
		logger.info("Server started");
		for(var i = 0; i < nbClients; i++) {
			Thread.ofPlatform().start(this::worker);
		}

	}

	private void silentlyClose(Closeable sc) {
		if(sc!= null) {
			try {
				sc.close();
			}catch(IOException e) {

			}
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 2) {
			System.out.println("usage: java ServerFixedPrestartedChatInt port nbClients");
			return;
		}
		var server = new ServerFixedPrestartedChatInt(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.launch();
	}
}