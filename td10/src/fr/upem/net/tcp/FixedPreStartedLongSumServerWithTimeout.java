package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPreStartedLongSumServerWithTimeout {

	private static final Logger logger = Logger.getLogger(FixedPreStartedLongSumServerWithTimeout.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private final int nbPrestart;
	private final ThreadData[] threadDatas;
	private int numberConnected =0;

	public FixedPreStartedLongSumServerWithTimeout(int port, int nbPrestart, int timeout) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		this.nbPrestart = nbPrestart;
		threadDatas = new ThreadData[nbPrestart];
		Arrays.setAll(threadDatas, e-> new ThreadData(timeout));
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	
	class ThreadData{
		private long lastTick;
		private final ReentrantLock lock = new ReentrantLock();
		private final long TIMEOUT;
		private SocketChannel client;
		
		
		ThreadData(long timeout){
			this.TIMEOUT = timeout;
		}
		
		public void setSocketChannel(SocketChannel client) {
			lock.lock();
			try {
				this.client=client;
				lastTick = System.currentTimeMillis();
			}finally {
				lock.unlock();
			}
		}
		
		public void tick() {
			lock.lock();
			try {
				lastTick = System.currentTimeMillis();
			}finally {
				lock.unlock();
			}
		}
		
		public void closeIfInactive() {
			lock.lock();
			try {
				if(TIMEOUT < System.currentTimeMillis() - lastTick) {
					silentlyClose(client);
					numberConnected--;
					tick();
				}
			}finally {
				lock.unlock();
			}
		}
		
		public void close() {
			lock.lock();
			try {
				try {
					client.close();
				} catch (IOException e) {
					logger.info("IOException get from forcing closing client");
				}
			}finally {
				lock.unlock();
			}
		}	
	}
	
	public void controls(String controls) {
		switch(controls.toUpperCase()) {
		case "INFO" ->{
			System.out.println("Connected Client :"+ numberConnected);
		}
		case "SHUTDOWN"->{
			try {
				serverSocketChannel.close();
			} catch (IOException e) {
				logger.info("IOException"+e);
			}
		}
		case "SHUTDOWNNOW"->{
			try {
				serverSocketChannel.close();
				Arrays.stream(threadDatas).forEach(ThreadData::close);
			} catch (IOException e) {
				logger.info("IOException"+e);
			}
		}
		default ->{
			throw new IllegalArgumentException();
		}
		}
	}
	
	
	public void console() {
		try(var scanner = new Scanner(System.in)){
			while(scanner.hasNextLine()) {
				var line = scanner.nextLine();
				controls(line);
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
		IntStream.range(0, nbPrestart).forEach( e -> Thread.ofPlatform().start(()->worker(e)));
		Thread.ofPlatform().start(()->{
			for(;;) {
				Arrays.stream(threadDatas).forEach(ThreadData::closeIfInactive);
			}
		});
		Thread.ofPlatform().start(this::console);
		
	}

	public void worker(int current) {
		while (!Thread.interrupted()) {
			try {
				SocketChannel client = serverSocketChannel.accept();
				numberConnected++;
				try {
					logger.info("Connection accepted from " + client.getRemoteAddress());
					
					threadDatas[current].setSocketChannel(client);
					serve(client,current);
				} catch (IOException ioe) {
					logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
				} finally {
					silentlyClose(client);
					numberConnected--;
				}
			}
			catch(IOException ioe) {
				logger.info("IOException");
			}
		}
	}

	/**
	 * Treat the connection sc applying the protocol. All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 */
	private void serve(SocketChannel sc,int current) throws IOException {
		for(;;) {
			var sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
			var buffer = ByteBuffer.allocate(Long.BYTES);
			long result =0L;
			sizeBuffer.clear();
			threadDatas[current].tick();
			if(!readFully(sc, sizeBuffer)) {
				return;
			}
			var size  = sizeBuffer.flip().getInt();

			while(size !=0) {
				threadDatas[current].tick();
				if(!readFully(sc, buffer)) {
					return;
				}
				result+= buffer.flip().getLong();
				size--;
				buffer.clear();
			}
			buffer.putLong(result);
			threadDatas[current].tick();
			sc.write(buffer.flip());
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
		if(args.length!=3) {
			return;
		}
		var server = new FixedPreStartedLongSumServerWithTimeout(Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]));
		server.launch();
	}
}