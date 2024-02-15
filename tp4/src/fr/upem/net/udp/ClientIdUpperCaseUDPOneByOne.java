package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;
	private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private long lastTime;

	private record Response(long id, String message) {
	};

	private final String inFilename;
	private final String outFilename;
	private final long timeout;
	private final InetSocketAddress server;
	private final DatagramChannel dc;
	private final SynchronousQueue<Response> queue = new SynchronousQueue<>();

	public static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	public ClientIdUpperCaseUDPOneByOne(String inFilename, String outFilename, long timeout, InetSocketAddress server)
			throws IOException {
		this.inFilename = Objects.requireNonNull(inFilename);
		this.outFilename = Objects.requireNonNull(outFilename);
		this.timeout = timeout;
		this.server = server;
		this.dc = DatagramChannel.open();
		dc.bind(null);
	}

	private void listenerThreadRun() {
		// TODO Listener thread run
		for(;;) {
			receiveBuffer.clear();
			try {
				dc.receive(receiveBuffer);
				receiveBuffer.flip();
				if(receiveBuffer.remaining() <= Long.BYTES) {
					logger.info("Nothing received");
					return;
				}
				var id  = receiveBuffer.getLong();
				if(!receiveBuffer.hasRemaining()) {
					logger.info("Nothing in the packet");
					return;
				}
				var msg = UTF8.decode(receiveBuffer).toString();
				queue.put(new Response(id, msg));
			}catch (AsynchronousCloseException | InterruptedException e) {
				logger.info("Closed Channel");
				return;
			}catch (IOException e) {
				logger.log(Level.SEVERE,"IOException",e);
				return;
			}
		}
	}

	public void launch() throws IOException, InterruptedException {
		try {

			var listenerThread = Thread.ofPlatform().start(this::listenerThreadRun);
			
			// Read all lines of inFilename opened in UTF-8
			var lines = Files.readAllLines(Path.of(inFilename), UTF8);

			var upperCaseLines = new ArrayList<String>();

			// TODO
			for(var line  = 0; line < lines.size(); line++) {
				sendBuffer.clear();
				sendBuffer.putLong(line);
				sendBuffer.put(UTF8.encode(lines.get(line)));
				Response response =null;
				while(response ==null || response.id() != line) {
					if(timeout <= System.currentTimeMillis()-lastTime) {
						sendBuffer.flip();
						dc.send(sendBuffer, server);
						lastTime = System.currentTimeMillis();
					}
					else {
						response = queue.poll(timeout - (System.currentTimeMillis() - lastTime),TimeUnit.MILLISECONDS);
					}
				}
				upperCaseLines.add(response.message());
			}

			listenerThread.interrupt();
			Files.write(Paths.get(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
		} finally {
			dc.close();
		}
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
		new ClientIdUpperCaseUDPOneByOne(inFilename, outFilename, timeout, server).launch();
	}
}
