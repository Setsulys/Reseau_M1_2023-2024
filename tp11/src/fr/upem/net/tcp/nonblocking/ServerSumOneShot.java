package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
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
			selector.select(this::treatKey);
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
		if (key.isValid() && key.isAcceptable()) {
			doAccept(key);
		}
		if (key.isValid() && key.isWritable()) {
			doWrite(key);
		}
		if (key.isValid() && key.isReadable()) {
			doRead(key);
		}
		}catch(AsynchronousCloseException e) {
			logger.info("Channel Closed");
		}catch(IOException e) {
			logger.severe("IOExeption"+e);
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
		   var aKey = sc.register(selector, SelectionKey.OP_READ);
		   aKey.attach(ByteBuffer.allocate(BUFFER_SIZE));
	}

	private void doRead(SelectionKey key) throws IOException {
		// TODO
		var buffer = (ByteBuffer) key.attachment(); 
		var channel = (SocketChannel) key.channel();
		if(channel.read(buffer)==-1) {
			silentlyClose(key);
			return;
		}
		if(buffer.hasRemaining()) {
			return;
		}
		buffer.flip();
		var result = buffer.getInt()+buffer.getInt();
		buffer.clear();
		buffer.putInt(result);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		// TODO
		var buffer = (ByteBuffer) key.attachment(); 
		var channel = (SocketChannel) key.channel();
		channel.write(buffer.flip());
		if(buffer.hasRemaining()) {
			return;
		}
		buffer.compact();
		key.interestOps(SelectionKey.OP_READ);
//		silentlyClose(key);
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