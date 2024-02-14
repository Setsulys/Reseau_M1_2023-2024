package fr.uge.net.udp.exam2223.ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerSlice {
    private static final Logger logger = Logger.getLogger(ServerSlice.class.getName());
    private final DatagramChannel datagramChannel;
    private final Selector selector;
    private final int port;
    private InetSocketAddress sender;
    
    private final static int BUFFER_SIZE = 128 * Long.BYTES;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final ByteBuffer sBuffer = ByteBuffer.allocateDirect(Long.BYTES);

    public ServerSlice(int port) throws IOException {
        this.port = port;
        this.selector = Selector.open();
        this.datagramChannel = DatagramChannel.open(); 
    }

    public void serve() throws IOException {
        // TODO bind and register the datagramChannels to the selector
    	datagramChannel.configureBlocking(false);
    	datagramChannel.bind(new InetSocketAddress(port));
    	datagramChannel.register(selector, SelectionKey.OP_READ);
        logger.info("ServerSlice started on port " + port);
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
    	buffer.clear();
    	var sender = channel.receive(buffer);
    	if(sender ==null) {
    		logger.info("The selector decieved us");
    		return;
    	}
//    	sender = (InetSocketAddress)checkSender;
    	buffer.flip();
    	key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
    	var channel = (DatagramChannel) key.channel();
    	sBuffer.clear();
    	var oldPosition = buffer.position();
    	sBuffer.putLong(buffer.getLong());
    	channel.send(sBuffer.flip(), sender);
    	if(sBuffer.hasRemaining()) {
    		logger.info("Packet Not sended");
    		buffer.position(oldPosition);
    		return;
    	}
    	if(!buffer.hasRemaining()) {
    		logger.info("All longs sended");
    		key.interestOps(SelectionKey.OP_READ);
    	}
    	
    }

    public static void usage() {
        System.out.println("Usage : ServerSlice port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerSlice(Integer.parseInt(args[0])).serve();
    }
}
