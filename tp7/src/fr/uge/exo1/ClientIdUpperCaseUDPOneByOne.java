package fr.uge.exo1;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = Charset.forName("UTF8");
	private static final int BUFFER_SIZE = 1024;

	private enum State {
		SENDING, RECEIVING, FINISHED
	};

	private final List<String> lines;
	private final List<String> upperCaseLines = new ArrayList<>();
	private final long timeout;
	private final InetSocketAddress serverAddress;
	private final DatagramChannel dc;
	private final Selector selector;
	private final SelectionKey uniqueKey;

	// TODO add new fields
	private final ByteBuffer rBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final ByteBuffer sBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

	private long lastTime;
	private int currentLine;

	private State state;

	private static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	private ClientIdUpperCaseUDPOneByOne(List<String> lines, long timeout, InetSocketAddress serverAddress,
			DatagramChannel dc, Selector selector, SelectionKey uniqueKey){
		this.lines = lines;
		this.timeout = timeout;
		this.serverAddress = serverAddress;
		this.dc = dc;
		this.selector = selector;
		this.uniqueKey = uniqueKey;
		this.state = State.SENDING;
	}

	public static ClientIdUpperCaseUDPOneByOne create(String inFilename, long timeout,
			InetSocketAddress serverAddress) throws IOException {
		Objects.requireNonNull(inFilename);
		Objects.requireNonNull(serverAddress);
		Objects.checkIndex(timeout, Long.MAX_VALUE);

		// Read all lines of inFilename opened in UTF-8
		var lines = Files.readAllLines(Path.of(inFilename), UTF8);
		var dc = DatagramChannel.open();
		dc.configureBlocking(false);
		dc.bind(null);
		var selector = Selector.open();
		var uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
		return new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress, dc, selector, uniqueKey);
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 5) {
			usage();
			return;
		}

		var inFilename = args[0];
		var outFilename = args[1];
		var timeout = Long.parseLong(args[2]);
		var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

		// Create client with the parameters and launch it
		var upperCaseLines = create(inFilename, timeout, server).launch();

		Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
	}

	private List<String> launch() throws IOException, InterruptedException {
		try {
			while (!isFinished()) {
				try {
					selector.select(this::treatKey, updateInterestOps());
				} catch (UncheckedIOException tunneled) {
					throw tunneled.getCause();
				}
			}
			return upperCaseLines;
		} finally {
			dc.close();
		}
	}

	private void treatKey(SelectionKey key) {
		try {
			if (key.isValid() && key.isWritable()) {
				doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				doRead();
			}
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * Updates the interestOps on key based on state of the context
	 *
	 * @return the timeout for the next select (0 means no timeout)
	 */

	private long updateInterestOps() {
		if(state == State.SENDING) {
			uniqueKey.interestOps(SelectionKey.OP_WRITE);
			return 0;
		}
		var tm = System.currentTimeMillis() - lastTime;
		if(tm< timeout) {
			uniqueKey.interestOps(SelectionKey.OP_READ);
			return timeout - tm;
		}
		state = State.SENDING;
		uniqueKey.interestOps(SelectionKey.OP_WRITE);
		return 0;
	}

	private boolean isFinished() {
		return state == State.FINISHED;
	}

	/**
	 * Performs the receptions of packets
	 *
	 * @throws IOException
	 */

	private void doRead() throws IOException {
		// TODO
		rBuffer.clear();
		var sender =dc.receive(rBuffer);
		if(sender ==null) {
			logger.info("No packet received");
		}
		rBuffer.flip();
		if(rBuffer.remaining() < Long.BYTES) {
			logger.info("Malformed packet");
			return;
		}
		var id = rBuffer.getLong();
		if(id != currentLine) {
			logger.info("Wrong packet");
			return;
		}
		upperCaseLines.add(UTF8.decode(rBuffer).toString());
		currentLine++;
		if(upperCaseLines.size() == lines.size()) {
			state = State.FINISHED;
			return;
		}
		state = State.SENDING;
	}

	/**
	 * Tries to send the packets
	 *
	 * @throws IOException
	 */

	private void doWrite() throws IOException {
		// TODO
		sBuffer.clear();
		sBuffer.putLong(currentLine);
		sBuffer.put(UTF8.encode(lines.get(currentLine)));
		sBuffer.flip();
		dc.send(sBuffer,serverAddress);
		if(sBuffer.hasRemaining()) {
			logger.info("Packet not sent");
			return;
		}
		state = State.RECEIVING;
	}
}