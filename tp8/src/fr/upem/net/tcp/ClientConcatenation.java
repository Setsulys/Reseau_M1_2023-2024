package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {

	public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
	
	private static String stringConcat(SocketChannel sc,ArrayList<ByteBuffer> list) throws IOException {
		ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
		intBuffer.putInt(list.size());
		sc.write(intBuffer.flip());
		for(var e :list) {
			sc.write(ByteBuffer.allocate(e.remaining()+Integer.BYTES).putInt(e.remaining()).put(e).flip());
		}
		
		intBuffer.clear();
		if(!readFully(sc, intBuffer)) {
			return null;
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(intBuffer.flip().getInt());
		if(!readFully(sc, buffer)) {
			return null;
		}
		
		return StandardCharsets.UTF_8.decode(buffer.flip()).toString();
	}
	
	static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
		while(buffer.hasRemaining()) {
			if(sc.read(buffer)==-1) {
				return false;
			}
		}
		return true;
	}
	
	public static void console(SocketChannel sc) throws IOException {
		ArrayList<ByteBuffer> list = new ArrayList<>();
		try(Scanner scanner = new Scanner(System.in)){
			while(scanner.hasNextLine()) {
				var line = scanner.nextLine();
				if(line !="") {
					list.add(StandardCharsets.UTF_8.encode(line));
				}
				else {
					System.out.println("Concatened String : " + stringConcat(sc,list));
				}
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		var server = new InetSocketAddress(args[0],Integer.parseInt(args[1]));
		try(var sc = SocketChannel.open(server)){
			console(sc);
		}
	}
}
