package fr.upem.net.tcp.exam2223.ex1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientWhois {
    private static final Logger logger = Logger.getLogger(fr.upem.net.tcp.exam2223.ex1.ClientWhois.class.getName());

    private final InetSocketAddress serverAddress;
    private final SocketChannel socketChannel;
    private static int ADDRESS_SIZE_MAX =Byte.BYTES *17+ Integer.BYTES;
    private final ByteBuffer bufferOut = ByteBuffer.allocate(Integer.BYTES);
    
    public ClientWhois(InetSocketAddress serverAddress) throws IOException {
        this.serverAddress = Objects.requireNonNull(serverAddress);
        this.socketChannel = SocketChannel.open(serverAddress);
    }

    public List<InetSocketAddress> performRequest(int max) throws IOException {
        List<InetSocketAddress> addresses = new ArrayList<>();
    	ByteBuffer bufferIn = ByteBuffer.allocate(Integer.BYTES + max*ADDRESS_SIZE_MAX);
    	var ipv4Byte = new byte[4];
    	var ipv6Byte = new byte[16];
    	InetAddress address;
    	bufferOut.clear();
    	socketChannel.write(bufferOut.putInt(max).flip());

    	socketChannel.read(bufferIn);
    	bufferIn.flip();
    	if(bufferIn.remaining() < Integer.BYTES) {
    		return null;
    	}
    	var numberOfAddresses = bufferIn.getInt();
    	for(var i = 0; i< numberOfAddresses; i++) {
    		var ipv =bufferIn.get();
    		if(ipv ==4) {
    			var oldLimit =bufferIn.limit();
    			bufferIn.limit(bufferIn.position()+4);
    			bufferIn.get(ipv4Byte);
    			bufferIn.limit(oldLimit);
    			address = InetAddress.getByAddress(ipv4Byte);
    		}
    		else if(ipv ==6) {
    			var oldLimit =bufferIn.limit();
    			bufferIn.limit(bufferIn.position()+16);
    			bufferIn.get(ipv6Byte);
    			bufferIn.limit(oldLimit);
    			address = InetAddress.getByAddress(ipv6Byte);
    		}
    		else {
    			return null;
    		}
    		var port = bufferIn.getInt();
    		addresses.add(new InetSocketAddress(address,port));
    	}
    	if(bufferIn.hasRemaining()) {
    		return null;
    	}
        return addresses;
    }

    public void launch() throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("How many addresses maximum do you want to get ?");
            while (scanner.hasNextInt()) {
                var max = scanner.nextInt();
                if (max < 0) {
                    System.out.println("max must be positive");
                    continue;
                }
                var answer = performRequest(max);
                if (answer == null) {
                    System.out.println("Problem with request, exiting program");
                    return;
                }
                System.out.println(answer);
                System.out.println("How many addresses maximum do you want to get ?");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("usage: java fr.uge.ex1.ClientWhois host port");
            return;
        }
        var serverAddress = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        new ClientWhois(serverAddress).launch();
    }
}