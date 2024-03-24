package fr.uge.exo5;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServerWithTimeoutAndShutDown {

	private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServerWithTimeoutAndShutDown.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private final Thread[] threads;
	private final int nbPermits;
	private final ThreadData[] threadDatas;
	private final long TIMEOUT;
	private int connected;

	public FixedPrestartedLongSumServerWithTimeoutAndShutDown(int port, int nbPermit,int timeout) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
		nbPermits = nbPermit;
		threads = new Thread[nbPermit];
		threadDatas = new ThreadData[nbPermits];
		TIMEOUT = timeout;
	}

	class ThreadData{
		private SocketChannel client;
		private long lastRead;
		private static long TIMEOUT;
		private final ReentrantLock lock = new ReentrantLock();

		ThreadData(long timeout){
			TIMEOUT=timeout;
		}

		void setSocketChannel(SocketChannel newClient){
			this.client = newClient;
		}

		void tick() {
			lock.lock();
			try {
				lastRead = System.currentTimeMillis();
			}finally {
				lock.unlock();
			}
		}

		void closeIfInactive() {
			lock.lock();
			try {
				if(System.currentTimeMillis()-lastRead >= TIMEOUT){
					silentlyClose(client);
				}
			}finally {
				lock.unlock();
			}
		}

		void close() {
			lock.lock();
			try {
				silentlyClose(client);
			}finally {
				lock.unlock();
			}
		}	
	}

	private void worker(int current){
		try {
			for(;;) {
				SocketChannel client = serverSocketChannel.accept();
				threadDatas[current].setSocketChannel(client);
				threadDatas[current].tick();
				connected++;
				try {
					logger.info("Connection accepted from " + client.getRemoteAddress());
					serve(client,current);
				} catch (IOException ioe) {
					logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
				} finally {
					threadDatas[current].close();
					//silentlyClose(client);
					connected--;
				}
			}
		}catch(IOException e){

		}finally {
			silentlyClose(serverSocketChannel);
		}

	}

	private void console(){
		try(Scanner scanner = new Scanner(System.in)){
			while(scanner.hasNextLine()) {
				var line = scanner.nextLine();
				switch(line){
				case "INFO" ->{logger.info("Connected Clients : "+connected);}
				case "SHUTDOWN" ->{
					try {
						serverSocketChannel.close();
					} catch (IOException e) {
						return;
					}
					logger.info("Refusing all new connexions");
				}
				case "SHUTDOWNNOW" ->{
					try {
						serverSocketChannel.close();
						for(var i=0; i < nbPermits; i++) {
							threadDatas[i].close();
						}
					} catch (IOException e) {
						return;
					}finally{

						logger.info("Shuting down the server");
					}
				}
				default ->{logger.info("Wrong input");}
				}
			}
		}
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		for(var i=0; i < nbPermits; i++) {
			var current=i;
			threadDatas[current] = new ThreadData(TIMEOUT);
			threads[current] = Thread.ofPlatform().start(()-> worker(current));
		}
		Thread.ofPlatform().name("supervisor").start(()->{
			for(;;) {
				for(var i=0; i < nbPermits;i++) {
					threadDatas[i].closeIfInactive();
				}
			}
		});
		Thread.ofPlatform().name("console").start(()->{
			for(;;) {
				console();
			}
		});

	}


	/**
	 * Treat the connection sc applying the protocol. All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 */
	private void serve(SocketChannel sc,int current) throws IOException {

		// TODO
		while(true) {
			ByteBuffer buffInt = ByteBuffer.allocate(Integer.BYTES);
			threadDatas[current].tick();
			if(!readFully(sc, buffInt)) {
				logger.info("Channel Closed");
				return;
			}
			var size = buffInt.flip().getInt();
			long sum =0;
			ByteBuffer buffLong = ByteBuffer.allocate(Long.BYTES);
			threadDatas[current].tick();
			while(size!=0) {
				buffLong.clear();
				if(!readFully(sc, buffLong)) {
					logger.info("Channel Closed");
					return;
				}
				sum += buffLong.flip().getLong();
				size--;
			}
			buffLong.clear();
			buffLong.putLong(sum);
			threadDatas[current].tick();
			sc.write(buffLong.flip());
		}

	}

	/**
	 * Close a SocketChannel while ignoring IOExecption
	 *
	 * @param sc
	 */

	private void silentlyClose(Closeable sc) {
		if (sc != null) {
			try {
				sc.close();
			} catch (IOException e) {
				// Do nothing
			}
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

	public static void main(String[] args) throws NumberFormatException, IOException {
		var server = new FixedPrestartedLongSumServerWithTimeoutAndShutDown(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]));
		server.launch();
	}
}