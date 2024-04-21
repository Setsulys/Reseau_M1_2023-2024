package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientChat {

	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
		private final ArrayDeque<Message> queue = new ArrayDeque<>();
		private boolean closed = false;
		private final MessageReader reader = new MessageReader();

		private Context(SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		/**
		 * Process the content of bufferIn
		 *
		 * The convention is that bufferIn is in write-mode before the call to process
		 * and after the call
		 *
		 */
		private void processIn() {
			// TODO
			for(;;) {
				Reader.ProcessStatus read = reader.process(bufferIn);
				switch(read) {
				case DONE:
					var value = reader.get();
					System.out.println(value.login() + " : "+ value.message());
					reader.reset();
					break;
				case REFILL: 
					return;
				case ERROR: 
					silentlyClose();
					return;
				default:
					throw new IllegalStateException();
				}
			}
		}

		/**
		 * Add a message to the message queue, tries to fill bufferOut and
		 * updateInterestOps
		 *
		 * @param bb
		 */
		private void queueMessage(Message msg) {
			// TODO
			queue.add(msg);
			processOut();
			updateInterestOps();
		}

		/**
		 * Try to fill bufferOut from the message queue
		 *
		 */
		private void processOut() {
			// TODO
			while(bufferOut.hasRemaining() && !queue.isEmpty()) {
				var msg= queue.poll();
				var login = StandardCharsets.UTF_8.encode(msg.login());
				var message = StandardCharsets.UTF_8.encode(msg.message());
				if(bufferOut.remaining()< Integer.BYTES) {
					return;
				}
				bufferOut.putInt(login.remaining());
				if(bufferOut.remaining() < login.remaining()) {
					return;
				}
				bufferOut.put(login);
				if(bufferOut.remaining() < Integer.BYTES) {
					return;
				}
				bufferOut.putInt(message.remaining());
				if(bufferOut.remaining() < message.remaining()) {
					return;
				}
				bufferOut.put(message);
			}
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and of both ByteBuffers.
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * updateInterestOps and after the call. Also it is assumed that process has
		 * been be called just before updateInterestOps.
		 */

		private void updateInterestOps() {
			// TODO
			int interestOps = 0;
			if (bufferIn.hasRemaining() && !closed) {
				interestOps |= SelectionKey.OP_READ;
			}
			if (bufferOut.position() != 0) {
				interestOps |= SelectionKey.OP_WRITE;
			}
			if (interestOps == 0) {
				silentlyClose();
				return;
			}
			key.interestOps(interestOps);
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doRead and after the call
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {
			// TODO
			if(sc.read(bufferIn)==-1) {
				closed =true;
			}
			processIn();
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that both buffers are in write-mode before the call to
		 * doWrite and after the call
		 *
		 * @throws IOException
		 */

		private void doWrite() throws IOException {
			// TODO
			sc.write(bufferOut.flip());
			bufferOut.compact();
				processOut();
			updateInterestOps();
		}

		public void doConnect() throws IOException {
			// TODO
			if (!sc.finishConnect()) {
				return; // the selector gave a bad hint
			}
			key.interestOps(SelectionKey.OP_READ);
		}
	}

	private static int BUFFER_SIZE = 10_000;
	private static Logger logger = Logger.getLogger(ClientChat.class.getName());

	private final SocketChannel sc;
	private final Selector selector;
	private final InetSocketAddress serverAddress;
	private final String login;
	private final Thread console;
	private Context uniqueContext;
	private final ArrayDeque<Message> clientQueue = new ArrayDeque<>();
	private final Object lock = new Object();

	public ClientChat(String login, InetSocketAddress serverAddress) throws IOException {
		this.serverAddress = serverAddress;
		this.login = login;
		this.sc = SocketChannel.open();
		this.selector = Selector.open();
		this.console = Thread.ofPlatform().unstarted(this::consoleRun);
	}

	private void consoleRun() {
		try {
			try (var scanner = new Scanner(System.in)) {
				while (scanner.hasNextLine()) {
					var msg = scanner.nextLine();
					sendCommand(msg);
				}
			}
			logger.info("Console thread stopping");
		} catch (InterruptedException e) {
			logger.info("Console thread has been interrupted");
		}
	}

	/**
	 * Send instructions to the selector via a BlockingQueue and wake it up
	 *
	 * @param msg
	 * @throws InterruptedException
	 */

	private void sendCommand(String msg) throws InterruptedException {
		// TODO
		synchronized (lock) {
			clientQueue.add(new Message(login, msg));
			selector.wakeup();
		}

	}

	/**
	 * Processes the command from the BlockingQueue
	 */

	private void processCommands() {
		// TODO
		synchronized (lock) {
			while (!clientQueue.isEmpty()) {
				uniqueContext.queueMessage(clientQueue.poll());
			}
		}
	}

	public void launch() throws IOException {
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new Context(key);
		key.attach(uniqueContext);
		sc.connect(serverAddress);

		console.start();

		while (!Thread.interrupted()) {
			try {
				selector.select(this::treatKey);
				processCommands();
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
		}
	}

	private void treatKey(SelectionKey key) {
		try {
			if (key.isValid() && key.isConnectable()) {
				uniqueContext.doConnect();
			}
			if (key.isValid() && key.isWritable()) {
				uniqueContext.doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				uniqueContext.doRead();
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 3) {
			usage();
			return;
		}
		new ClientChat(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2]))).launch();
	}

	private static void usage() {
		System.out.println("Usage : ClientChat login hostname port");
	}
}