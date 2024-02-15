package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {
	public static final int BUFFER_SIZE = 1024;
	private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());
	private static final int TIMEOUT =1000;

	private static void usage() {
		System.out.println("Usage : NetcatUDP host port charset");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			usage();
			return;
		}

		var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		var cs = Charset.forName(args[2]);
		var buffer = ByteBuffer.allocate(BUFFER_SIZE);
		var queue = new ArrayBlockingQueue<String>(1);
		try(DatagramChannel dc = DatagramChannel.open()){
			dc.bind(null);


			Thread.ofPlatform().start(()->{
				try {
					for(;;) {
						buffer.clear();
						var sender = dc.receive(buffer);
						buffer.flip();
						System.out.println("Received "+buffer.remaining()+" bytes from"+ sender);
						var msg = cs.decode(buffer).toString();
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
			try (var scanner = new Scanner(System.in);) {
				while (scanner.hasNextLine()) {
					var line = scanner.nextLine();
					// TODO
					String msg;
					do {
						dc.send(cs.encode(line),server);
						try {
							msg = queue.poll(TIMEOUT,TimeUnit.MILLISECONDS);
						} catch (InterruptedException e) {
							logger.info("Interrupted Thread");
							return;
						}
						if(msg == null) {
							logger.info("Le server n'a pas répondu");
						}
					}while(msg == null);
					System.out.println("String : " + msg);
				}
			}
		}
	}
}
