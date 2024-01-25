package fr.uge.exo3;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
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
	private final static Charset UTF8 = StandardCharsets.UTF_8;
	private final static int BUFFER_SIZE = 1024;
	private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());

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
		var queue = new ArrayBlockingQueue<String>(1);
		var bb = ByteBuffer.allocate(BUFFER_SIZE);
		// Read all lines of inFilename opened in UTF-8
		var lines = Files.readAllLines(Path.of(inFilename), UTF8);
		var upperCaseLines = new ArrayList<String>();
		try(var dc =DatagramChannel.open()){
			dc.bind(null);
			var receiver = Thread.ofPlatform().start(()->{
				for(;!Thread.currentThread().isInterrupted();) {
					try {
						bb.clear();
						dc.receive(bb);
						bb.flip();
						var msg = UTF8.decode(bb).toString();
						queue.put(msg);
					}catch(ClosedByInterruptException e) {
						logger.info("Channel Closed");
					} catch (AsynchronousCloseException e) {
						logger.info("Channel Closed ");
					} catch (IOException e) {
						logger.log(Level.SEVERE,"IOException ",e);
					} catch (InterruptedException e) {
						logger.info("Interrupted Exception ");
					}				
				}
			});
			for(var line : lines) {
				String msg = null;
				while(msg ==null) {
					dc.send(UTF8.encode(line), server);
					msg=queue.poll(timeout,TimeUnit.MILLISECONDS);
				}
				upperCaseLines.add(msg);
			}
			receiver.interrupt();
		}

		// Write upperCaseLines to outFilename in UTF-8
		Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
	}
}