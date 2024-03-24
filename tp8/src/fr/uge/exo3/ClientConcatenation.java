package fr.uge.exo3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {
	public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
	public static final Charset UTF8 = StandardCharsets.UTF_8;
	
	public static String requestConcatenation(SocketChannel sc,List<ByteBuffer> lines) throws IOException {
		var size = lines.size();
		var numberOfoctets = lines.stream().mapToInt(e ->e.remaining()).sum();
		ByteBuffer buffer = ByteBuffer.allocate((size+1)*Integer.BYTES + numberOfoctets* Byte.BYTES);
		ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
		
		buffer.putInt(size);
		for(var currentLine : lines) {
			buffer.putInt(currentLine.remaining());
			buffer.put(currentLine);
		}
		lines.clear();
		buffer.flip();
		sc.write(buffer);
		
		if(!readFully(sc, intBuffer)){
			logger.info("NOthing received");
			return null;
		}
		ByteBuffer receiveBuffer = ByteBuffer.allocate(intBuffer.flip().getInt());
		if(!readFully(sc, receiveBuffer)){
			logger.info("Nothing received");
			return null;
		}
		return UTF8.decode(receiveBuffer.flip()).toString();
	}
	
	
	public static void concatenation(SocketChannel sc) throws IOException {
		var lines = new ArrayList<ByteBuffer>();
		try(var scanner = new Scanner(System.in)){
			while(scanner.hasNextLine()) {
				var line = scanner.nextLine();
				if(line!="") {
					lines.add(UTF8.encode(line));
				}
				else {
					System.out.println("Concatenated String : "+requestConcatenation(sc,lines));
					
				}
			}
		}
	}
	
    public static ByteBuffer storing(ByteBuffer buffer) {
    	ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
    	return newBuffer.put(buffer.flip());
    }
	
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
    	int read=0;
    	while(buffer.hasRemaining() && read !=-1) {;
    		read = sc.read(buffer);
    	}
        return read !=-1;
    }
	
	public static void main(String[] args) throws IOException {
		var server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
		try (var sc = SocketChannel.open(server)){
			concatenation(sc);
		}
	}
}
