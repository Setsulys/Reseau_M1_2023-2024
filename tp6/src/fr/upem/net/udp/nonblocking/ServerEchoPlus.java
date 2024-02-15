package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoPlus {
    private static final Logger logger = Logger.getLogger(ServerEchoPlus.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private int port;

    public ServerEchoPlus(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        // TODO set dc in non-blocking mode and register it to the selector
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + port);
        while (!Thread.interrupted()) {
        	try {
            selector.select(this::treatKey);
        	}catch(UncheckedIOException tunneled) {
        		tunneled.getCause();
        	}
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        // TODO
    	var channel = (DatagramChannel) key.channel();
    	sender = channel.receive(buffer);
    	if(sender == null) {
    		logger.info("Nothing received");
    		return;
    	}
    	buffer.flip();
    	while(buffer.hasRemaining()) {
    		var oldPosition = buffer.position();
    		var toUp = buffer.get();
    		buffer.position(oldPosition);
    		buffer.put((byte)((toUp+1)%256));
    		
    	}
    	buffer.flip();
    	key.interestOps(SelectionKey.OP_WRITE);
    	
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
    	var channel = (DatagramChannel) key.channel();
    	channel.send(buffer, sender);
    	if(buffer.hasRemaining()) {
    		buffer.flip();
    		return;
    	}
    	key.interestOps(SelectionKey.OP_READ);
    	
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoPlus(Integer.parseInt(args[0])).serve();
    }
}
