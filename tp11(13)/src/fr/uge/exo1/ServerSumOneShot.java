package fr.uge.exo1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerSumOneShot {

	private static final int BUFFER_SIZE = 2 * Integer.BYTES;
	private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerSumOneShot(int port) throws IOException {
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
			}catch(AsynchronousCloseException e) {
				logger.info("Closed channel");
				return;
			}catch(IOException e) {
				logger.severe("IOException : " + e);
				return;
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
		}catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);

			}
		}catch(IOException e) {
			silentlyClose(key);
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			return;
		}

	}

	private void doAccept(SelectionKey key) throws IOException {
		// TODO
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if(sc == null) {
			logger.info("the selector gave bad hint");
			return;
		}
		sc.configureBlocking(false);
		var sKey = sc.register(selector, SelectionKey.OP_READ);
		sKey.attach(ByteBuffer.allocate(BUFFER_SIZE));

	}

	private void doRead(SelectionKey key) throws IOException {
		// TODO
		var buffer = (ByteBuffer) key.attachment();
		var client = (SocketChannel) key.channel();
		if(client.read(buffer)==-1) {
			logger.info("closed channel");
			silentlyClose(key);
			return;
		}

		if(buffer.hasRemaining()) {
			logger.info("Not all received");
			return;
		}
		buffer.flip();
		var sum = buffer.getInt()+buffer.getInt();
		buffer.clear();
		buffer.putInt(sum);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		// TODO
		var buffer = (ByteBuffer) key.attachment();
		var client = (SocketChannel) key.channel();
		buffer.flip();
		client.write(buffer);
		if(buffer.hasRemaining()) {
			logger.info("Not all sended");
			return;
		}
		//silentlyClose(key);
		buffer.compact();
		key.interestOps(SelectionKey.OP_READ);
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
		new ServerSumOneShot(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumOneShot port");
	}
}