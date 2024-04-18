package fr.upem.net.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedLongSumServer {

	private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;
	private final int nbPrestart;


	public FixedPrestartedLongSumServer(int port, int nbPrestart) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		this.nbPrestart = nbPrestart;
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		IntStream.range(0, nbPrestart).forEach( e -> Thread.ofPlatform().start(this::worker));
	}

	public void worker() {
		while (!Thread.interrupted()) {
			try {
				SocketChannel client = serverSocketChannel.accept();
				try {
					logger.info("Connection accepted from " + client.getRemoteAddress());
					serve(client);
				} catch (IOException ioe) {
					logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
				} finally {
					silentlyClose(client);
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
	private void serve(SocketChannel sc) throws IOException {
		for(;;) {
			var sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
			var buffer = ByteBuffer.allocate(Long.BYTES);
			long result =0L;
			sizeBuffer.clear();
			if(!readFully(sc, sizeBuffer)) {
				return;
			}
			var size  = sizeBuffer.flip().getInt();

			while(size !=0) {
				if(!readFully(sc, buffer)) {
					return;
				}
				result+= buffer.flip().getLong();
				size--;
				buffer.clear();
			}
			buffer.putLong(result);
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
		if(args.length!=2) {
			return;
		}
		var server = new FixedPrestartedLongSumServer(Integer.parseInt(args[0]),Integer.parseInt(args[1]));
		server.launch();
	}
}