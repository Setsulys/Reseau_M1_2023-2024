package fr.uge.exo3;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServer {

	private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private final Thread[] threads;
	private final int nbPermits;

	public FixedPrestartedLongSumServer(int port, int nbPermit) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
		nbPermits = nbPermit;
		threads = new Thread[nbPermit];
	}

	private void worker(){
		try {
			for(;;) {
				SocketChannel client = serverSocketChannel.accept();
				try {
					logger.info("Connection accepted from " + client.getRemoteAddress());
					serve(client);
				} catch (IOException ioe) {
					//
				} finally {
					silentlyClose(client);
				}
			}
		}catch(IOException e){
			logger.log(Level.SEVERE,"IOException",e);
		}finally {
			silentlyClose(serverSocketChannel);
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
			threads[i]=Thread.ofPlatform().start(this::worker);
		}
	}

	/**
	 * Treat the connection sc applying the protocol. All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 */
	private void serve(SocketChannel sc) throws IOException {

		// TODO
		while(true) {
			ByteBuffer buffInt = ByteBuffer.allocate(Integer.BYTES);
			if(!readFully(sc, buffInt)) {
				logger.info("Channel Closed");
				return;
			}

			var size = buffInt.flip().getInt();
			long sum =0;
			ByteBuffer buffLong = ByteBuffer.allocate(Long.BYTES);
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
		var server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]),Integer.parseInt(args[1]));
		server.launch();
	}
}