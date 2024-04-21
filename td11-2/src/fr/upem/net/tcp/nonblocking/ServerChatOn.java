package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatOn {
	static private class Context {
		private final SelectionKey key;
		private final SocketChannel sc;
		private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
		private final ArrayDeque<Message> queue = new ArrayDeque<>();
		private final ServerChatOn server; // we could also have Context as an instance class, which would naturally
		// give access to ServerChatInt.this
		private boolean closed = false;
		private final MessageReader messageReader = new MessageReader();
		private static final Charset UTF8 = StandardCharsets.UTF_8;

		private Context(ServerChatOn server, SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
			this.server = server;
		}

		/**
		 * Process the content of bufferIn
		 *
		 * The convention is that bufferIn is in write-mode before the call to process and
		 * after the call
		 *
		 */
		  private void processIn() {
			    for (;;) {
			      Reader.ProcessStatus status = messageReader.process(bufferIn);
			      switch (status) {
			      case DONE:
			        var value = messageReader.get();
			        server.broadcast(value);
			        messageReader.reset();
			        break;
			      case REFILL:
			        return;
			      case ERROR:
			        silentlyClose();
			        return;
			      }
			    }
			  }

		/**
		 * Add a message to the message queue, tries to fill bufferOut and updateInterestOps
		 *
		 * @param msg
		 */
		public void queueMessage(Message msg) {
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
				var message = queue.poll();
				var login = UTF8.encode(message.login());
				var txt = UTF8.encode(message.message());
				if(bufferOut.remaining()<Integer.BYTES) {
					return;
				}
				bufferOut.putInt(login.remaining());
				if(bufferOut.remaining()<login.remaining()*Byte.BYTES) {
					return;
				}
				bufferOut.put(login);
				if(bufferOut.remaining()<Integer.BYTES) {
					return;
				}
				bufferOut.putInt(txt.remaining());
				if(bufferOut.remaining()<txt.remaining()*Byte.BYTES) {
					return;
				}
				bufferOut.put(txt);
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
			var interestOps=0;
			if(!closed && bufferIn.hasRemaining()) {
				interestOps |= SelectionKey.OP_READ;
			}
			if(bufferOut.position()!=0) {
				interestOps |=SelectionKey.OP_WRITE;
			}
			if(interestOps ==0) {
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
				closed = true;
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

	}

	private static final int BUFFER_SIZE = 1_024;
	private static final Logger logger = Logger.getLogger(ServerChatOn.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerChatOn(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
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
		// TODO
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return; // the selector gave a bad hint
		}
		sc.configureBlocking(false);
		var newKey = sc.register(selector, SelectionKey.OP_READ);
		newKey.attach(new Context(this,newKey));
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/**
	 * Add a message to all connected clients queue
	 *
	 * @param msg
	 */
	private void broadcast(Message msg) {
		// TODO
		selector.keys().stream().map(e-> (Context)e.attachment()).filter(f->f!=null).forEach(context -> context.queueMessage(msg));	
//		for(var key : selector.keys()) {
//			var context = (Context) key.attachment();
//			if(context != null) {
//				context.queueMessage(msg);
//			}
//		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerChatOn(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumBetter port");
	}
}