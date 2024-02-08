	package fr.uge.exo3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Objects;
import java.util.logging.Logger;

import javax.xml.crypto.Data;

public class ServerEchoMultiPort {
    private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());

    //private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    //private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private final int portInf;
    private final int portSup;
    
    class Context{
    	
    	
    	private final DatagramChannel dc;
    	private final ByteBuffer contextBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    	private final SelectionKey key;
    	
    	private Context(DatagramChannel dc,SelectionKey key) {
    		Objects.requireNonNull(dc);
    		Objects.requireNonNull(key);
    		this.dc =dc;
    		this.key=key;
    		
    	}
    	
    	public void doRead() throws IOException {
            contextBuffer.clear();
            var channel = (DatagramChannel) key.channel();
            var sendFrom =channel.receive(contextBuffer);
        	if(sendFrom==null) {
        		logger.warning("The selector decieved us");
        		return;
        	}
        	sender =sendFrom;
        	contextBuffer.flip();
        	key.interestOps(SelectionKey.OP_WRITE);
    	}
    	
        private void doWrite() throws IOException {
        	var channel = (DatagramChannel) key.channel();
        	channel.send(contextBuffer, sender);
        	if(contextBuffer.hasRemaining()) {
        		logger.warning("Packet has not been sended");
        		return;
        	}
        	key.interestOps(SelectionKey.OP_READ);
        }
    	
    }

    public ServerEchoMultiPort(int portInf, int portSup) throws IOException {
        this.portInf = portInf;
        this.portSup = portSup;
        selector = Selector.open();
        
        for(var port = portInf; port <= portSup;port++) {
        	var dc = DatagramChannel.open();
            dc.bind(new InetSocketAddress(port));
            dc.configureBlocking(false);
            var key = dc.register(selector, SelectionKey.OP_READ);
            key.attach(new Context(dc,key));
        }

    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port ");
        while (!Thread.interrupted()) {
        	try {
        		selector.select(this::treatKey);
        	}catch(UncheckedIOException tunneled) {
        		throw tunneled.getCause();
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
    	var context = (Context) key.attachment();
    	context.doRead();
    }

    private void doWrite(SelectionKey key) throws IOException {
    	var context = (Context) key.attachment();
    	context.doWrite();
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port port2");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerEchoMultiPort(Integer.parseInt(args[0]),Integer.parseInt(args[1])).serve();
    }
}
