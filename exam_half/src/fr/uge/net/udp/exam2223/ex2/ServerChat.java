package fr.uge.net.udp.exam2223.ex2;


import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ServerChat {

    private static final Logger logger = Logger.getLogger(ServerChat.class.getName());
    private final DatagramChannel datagramChannel;
    private final int port;
    private final Charset UTF8 = StandardCharsets.UTF_8;
    
    private final Map<String,InetSocketAddress> listeners = new HashMap<>();
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(2048);
    private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(2048);

    public ServerChat(int port) throws IOException {
        this.datagramChannel = DatagramChannel.open();
        this.port = port;
    }

    public void serve() throws IOException {
        datagramChannel.bind(new InetSocketAddress(port));
        System.out.println("ServerChat started on port " + port);
        // TODO
        for(;;) {
        	buffer.clear();
        	var sender =datagramChannel.receive(buffer);
        	buffer.flip();
        	if(!buffer.hasRemaining()) {
        		logger.info("Nothing received");
        		return;
        	}
        	//Sender
        	var senderNameSize =buffer.getInt();
        	var oldLimit = buffer.limit();
        	buffer.limit(buffer.position()+senderNameSize);
        	var senderName = UTF8.decode(buffer).toString();
        	listeners.putIfAbsent(senderName,(InetSocketAddress)sender);       	
        	buffer.limit(oldLimit);
        	
        	//Receiver
        	var receiverNameSize = buffer.getInt();
        	oldLimit = buffer.limit();
        	buffer.limit(buffer.position()+receiverNameSize);
        	var receiverName = UTF8.decode(buffer).toString();
        	System.out.println(listeners.keySet());
        	if(listeners.containsKey(receiverName)) {
        		buffer.limit(oldLimit);
            	//Data
            	var dataSize = buffer.getInt();
            	var sendTo = listeners.get(receiverName);
            	
            	//create sending buffer
            	sendBuffer.clear();
            	sendBuffer.putInt(senderNameSize);
            	sendBuffer.put(UTF8.encode(senderName));
            	sendBuffer.putInt(dataSize);
            	sendBuffer.put(buffer);
            	sendBuffer.flip();
            	datagramChannel.send(sendBuffer, sendTo);
        	}
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerChat port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        int port = Integer.valueOf(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            System.out.println("The port number must be between 1024 and 65535");
            return;
        }

        var server=new ServerChat(port);
        try {
            server.serve();
        } catch (BindException e) {
            System.err.println("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
    }
}
