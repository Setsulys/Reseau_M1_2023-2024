package fr.uge.exo2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoWithConsole {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		private boolean closed = false;

		private Context(SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and the ByteBuffer buffer.
		 *
		 * The convention is that buff is in write-mode.
		 */
		private void updateInterestOps() {
			// TODO
			var interestOps = 0;
			if(buffer.position()!=0 && !closed) {
				interestOps |= SelectionKey.OP_WRITE;
			}
			if(buffer.hasRemaining() && !closed) {
				interestOps |= SelectionKey.OP_READ;
			}
			if(interestOps ==0) {
				silentlyClose();
				return;
			}
			key.interestOps(interestOps);
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that buffer is in write-mode before calling doRead and is in
		 * write-mode after calling doRead
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			// TODO
			if(sc.read(buffer)==-1) {
				logger.info("not all received");
				closed=true;
				updateInterestOps();
				return;
			}
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that buffer is in write-mode before calling doWrite and is in
		 * write-mode after calling doWrite
		 *
		 * @throws IOException
		 */
		private void doWrite() throws IOException {
			// TODO
			buffer.flip();
			sc.write(buffer);
			buffer.compact();
			updateInterestOps();
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(ServerEchoWithConsole.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final Thread console;

	public ServerEchoWithConsole(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		console = Thread.ofPlatform().unstarted(this::consoleRun);
	}

	public boolean clientConnected(SelectionKey selectedKey) {
		return (Context)selectedKey.attachment()!=null && !((Context)selectedKey.attachment()).closed;
	}

	private void consoleRun() {
		try (var scanner = new Scanner(System.in)){
			while (scanner.hasNextLine()) {
				var command = scanner.nextLine();
				switch(command) {
				case "INFO":{
					var nbClient =selector.keys().stream().filter(this::clientConnected).count();
					logger.info("Connected client : "+ nbClient);
					break;
				}
				case "SHUTDOWN":{
					logger.info("Shutdown");
					try {
						serverSocketChannel.close();
					}catch(AsynchronousCloseException e) {
						logger.info("Close server connections");
					}catch (IOException e) {
						logger.severe("IOExeption");
					}
					break;
				}
				case "SHUTDOWNNOW":{
					logger.info("ShutdownNow");
					selector.keys().forEach(this::silentlyClose);
					try {
						selector.close();
					} catch (IOException e) {;
					}
					try {
						Thread.currentThread().interrupt();
						serverSocketChannel.close();
					}catch(AsynchronousCloseException e) {
						logger.info("Close server connections");
						return;
					}catch (IOException e) {
						logger.severe("IOExeption");
						return;
					}
					break;
				}
				default:{
					logger.info("Available commands: \n - INFO\n - SHUTDOWN\n - SHUTDOWNNOW");
				}
				}
			}
		}
		logger.info("Console thread stopping");
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		console.start();
		while (!Thread.interrupted()) {
			Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel  sc = ssc.accept();
		if(sc == null) {
			logger.info("the selector gave bad hint");
			return;
		}
		sc.configureBlocking(false);
		var sKey = sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		sKey.attach(new Context(sKey));
	}

	private void silentlyClose(SelectionKey key) {
		var sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerEchoWithConsole(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerEcho port");
	}
}