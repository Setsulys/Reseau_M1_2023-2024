package fr.upem.net.udp;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPFile {
	private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());
	private final static Charset UTF8 = StandardCharsets.UTF_8;
	private final static int BUFFER_SIZE = 1024;

	private static void usage() {
		System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 5) {
			usage();
			return;
		}

		var inFilename = args[0];
		var outFilename = args[1];
		var timeout = Integer.parseInt(args[2]);
		var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

		// Read all lines of inFilename opened in UTF-8
		var lines = Files.readAllLines(Path.of(inFilename), UTF8);
		var upperCaseLines = new ArrayList<String>();
		var queue = new ArrayBlockingQueue<String>(10);
		var buffer = ByteBuffer.allocate(BUFFER_SIZE);
		// TODO
		try(DatagramChannel dc = DatagramChannel.open()){
			dc.bind(null);
			
			Thread.ofPlatform().start(()->{
				try {
					for(;;) {
						buffer.clear();
						var sender = dc.receive(buffer);
						buffer.flip();
						System.out.println("Received "+buffer.remaining()+" bytes from"+ sender);
						var msg = UTF8.decode(buffer).toString();
						queue.put(msg);
					}
				}catch(AsynchronousCloseException | InterruptedException e) {
					logger.info("Channel closed");
					return;
				}catch (IOException e) {
					logger.log(Level.SEVERE,"IOException",e);
					return;
				}

			});
			for(var line: lines) {
				String msg;
				do {
					dc.send(UTF8.encode(line),server);
					try {
						msg = queue.poll(timeout,TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						logger.info("Interrupted Thread");
						return;
					}
					if(msg == null) {
						logger.info("Le server n'a pas répondu");
					}
				}while(msg == null);
				upperCaseLines.add(msg);
			}
		}


		// Write upperCaseLines to outFilename in UTF-8
		Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
	}
}