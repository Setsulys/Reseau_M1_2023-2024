package fr.uge.exo2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPTimeout {
	public static final int BUFFER_SIZE = 1024;
	private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPTimeout.class.getName());

	private static void usage() {
		System.out.println("Usage : NetcatUDP host port charset");
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 3) {
			usage();
			return;
		}

		var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		var cs = Charset.forName(args[2]);
		var buffer = ByteBuffer.allocate(BUFFER_SIZE);

		var queue = new ArrayBlockingQueue<String>(1);
		var timeout=1000;
		try(var dc = DatagramChannel.open()){
			dc.bind(null);

			Thread.ofPlatform().start(()->{
				for(;!Thread.currentThread().isInterrupted();){
					try {
						dc.receive(buffer);
						buffer.flip();
						var msg = cs.decode(buffer).toString();
						queue.put(msg);
						logger.info(msg);
						buffer.clear();
					}catch(ClosedByInterruptException e) {
						logger.info("Channel Closed");
						return;
					} catch (AsynchronousCloseException e) {
						logger.info("Channel Closed ");
						return;
					} catch (IOException e) {
						logger.log(Level.SEVERE,"IOException ",e);
					} catch (InterruptedException e) {
						logger.info("Interrupted Exception ");
					}	
				}


			});

			try (var scanner = new Scanner(System.in);) {
				while (scanner.hasNextLine()) {
					var line = scanner.nextLine();
					dc.send(cs.encode(line), server);
					var msg = queue.poll(timeout,TimeUnit.MILLISECONDS);
					if(msg == null) {
						logger.info("Le server n'a pas répondu");
					}
				}
			}
		}
	}
}
