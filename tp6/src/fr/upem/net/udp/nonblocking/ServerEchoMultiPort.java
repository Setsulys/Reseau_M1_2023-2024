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

public class ServerEchoMultiPort {
    private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());

    private final Selector selector;
    private final int BUFFER_SIZE = 1024;

    private int portMin;
    private int portMax;

    class Context{
    	private final DatagramChannel dc;
    	private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    	private final SelectionKey key;
    	private SocketAddress sender;
    	Context(DatagramChannel dc,SelectionKey key){
    		this.dc = dc;
    		this.key = key;
    	}
    	
        private void doRead() throws IOException {
            // TODO
        	buffer.clear();
        	sender = dc.receive(buffer);
        	if(sender == null) {
        		logger.info("Nothing received");
        		return;
        	}
        	buffer.flip();
        	key.interestOps(SelectionKey.OP_WRITE);
        }

        private void doWrite() throws IOException {
            // TODO
        	dc.send(buffer, sender);
        	if(buffer.hasRemaining()) {
        		buffer.flip();
        		return;
        	}
        	key.interestOps(SelectionKey.OP_READ);
        }
    	
    }
    
    public ServerEchoMultiPort(int portMin,int portMax) throws IOException {
        this.portMin = portMin;
        this.portMax = portMax;
        selector = Selector.open();
        
        for(var port = portMin; port <= portMax;port++) {
        	var dc = DatagramChannel.open();
            dc.bind(new InetSocketAddress(port));
            // TODO set dc in non-blocking mode and register it to the selector
            dc.configureBlocking(false);
            var key = dc.register(selector, SelectionKey.OP_READ);
            key.attach(new Context(dc,key));
        }
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + portMin +" to port " + portMax);
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
    	var context = (Context) key.attachment();
    	context.doRead();
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
    	var context = (Context) key.attachment();
    	context.doWrite();
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho portMin portMax");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerEchoMultiPort(Integer.parseInt(args[0]),Integer.parseInt(args[1])).serve();
    }
}
