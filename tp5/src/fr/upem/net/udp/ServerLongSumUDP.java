package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class ServerLongSumUDP {
    private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    
    private final Map<SenderId,Data> route = new HashMap<>();
    
    
    private record SenderId(InetSocketAddress sender,Long sessionId) {
    	
    }
    
    private class Data{
    	private final BitSet answerLog;
    	private final int size;
    	private long sum = 0;
    	
    	Data(int size){
    		answerLog = new BitSet(size);
    		this.size = size;
    	}
    	
    	public void set(int index,long value) {
    		Objects.checkIndex(index, size);
    		if(!answerLog.get(index)) {
    			sum+=value;
    		}
    		answerLog.set(index);
    	}
    	
    	public List<Integer> notReceived(){
    		return IntStream.range(0, size).filter(i -> !answerLog.get(i)).boxed().toList();
    	}
    	
    	public long sum() {
    		return sum;
    	}

    	
    }
    
	public ServerLongSumUDP(int port) throws IOException{
		dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerBetterUpperCaseUDP started on port " + port);
	}
	public void serve() throws IOException{
		try {
			while(!Thread.interrupted()) {
				buffer.clear();
				var sender = dc.receive(buffer);
				buffer.flip();	
				if(buffer.remaining() == 4 *Long.BYTES + Byte.BYTES) {
					var opByte = buffer.get();
					if(opByte == 1) {
						var sessionId = buffer.getLong();
						var idPosOper = buffer.getLong();
						var totalOper = buffer.getLong();
						var opValue = buffer.getLong();
						
						//ack
						buffer.clear();
						buffer.put((byte)2);
						buffer.putLong(sessionId);
						buffer.putLong(idPosOper);
						buffer.flip();
						dc.send(buffer, sender);
						
						if(0 <= idPosOper || idPosOper < totalOper) {
							var data = route.computeIfAbsent(new SenderId((InetSocketAddress)sender, sessionId), k-> new Data(Long.valueOf(totalOper).intValue()));
							data.set(Long.valueOf(idPosOper).intValue(),opValue);
							if(data.notReceived().isEmpty()){
								buffer.clear();
								buffer.put((byte)3);
								buffer.putLong(sessionId);
								buffer.putLong(data.sum());
								buffer.flip();
								dc.send(buffer, sender);
							}
							
						}
						
					}
					
				}
				else {
					logger.info("wrong packetSize");
				}
				
			}
		}finally {
			dc.close();
		}
	}
	
    public static void usage() {
        System.out.println("Usage : ServerLongSumUDP port");
    }
	public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }

        var port = Integer.parseInt(args[0]);

        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }

        try {
            new ServerLongSumUDP(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
	}
}
