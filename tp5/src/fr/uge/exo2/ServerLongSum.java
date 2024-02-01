package fr.uge.exo2;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.BitSet;
import java.util.HashMap;
import java.util.logging.Logger;

public class ServerLongSum {
	private static final Logger logger = Logger.getLogger(ServerLongSum.class.getName());
	private static final int BUFFER_SIZE = 4* Long.BYTES+ Byte.BYTES;
	private static final int SEND_BUFFER_SIZE = 2* Long.BYTES+ Byte.BYTES;
	
	private final DatagramChannel dc;
	private final ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER_SIZE);
	private final ByteBuffer sBuffer = ByteBuffer.allocate(SEND_BUFFER_SIZE);
	
	
	private record Session(InetSocketAddress address,long idSession) {
	}
	
	private class SumClass{
		private final long totalOper;
		private final BitSet operations;
		public long sum;
		
		public SumClass(long totalOper) {
			if(totalOper < 0 || totalOper > Integer.MAX_VALUE) {
				throw new IllegalArgumentException();
			}
			this.totalOper =totalOper;
			operations = new BitSet(Long.valueOf(totalOper).intValue());
		}
		
		public void summing(long value,long idOper) {
			sum+=value;
			operations.set(Long.valueOf(idOper).intValue());
		}
		
		public boolean allSet() {
			return Long.valueOf(totalOper).intValue() == operations.cardinality();
		}
		
	}
	
	private final HashMap<Session,SumClass> datas = new HashMap<>();
	
	
	public ServerLongSum(int port) throws IOException{
		dc = DatagramChannel.open();
		dc.bind(new InetSocketAddress(port));
		logger.info("ServerLongSum started on port" + port);
	}
	
	public void serve() throws IOException{
		try {
			while(!Thread.interrupted()) {
				rBuffer.clear();
				var sender = (InetSocketAddress) dc.receive(rBuffer);
				logger.info("Received "+ rBuffer.position() + " byte from " +sender.toString());
				rBuffer.flip();
				if(rBuffer.remaining() < BUFFER_SIZE) {
					return;
				}
				var op = rBuffer.get();
				var sessionId = rBuffer.getLong();
				var idOper = rBuffer.getLong();
				var totalOper = rBuffer.getLong();
				var opValue = rBuffer.getLong();
				if(totalOper <=0 || totalOper < idOper) {
					return;
				}
				var data = datas.computeIfAbsent(new Session(sender,sessionId), v-> new SumClass(totalOper));
				data.summing(opValue,idOper);
				sBuffer.clear();
				if(!data.allSet()) {
					sBuffer.put(Integer.valueOf(2).byteValue());
					sBuffer.putLong(sessionId);
					sBuffer.putLong(idOper);
				}
				else {
					sBuffer.put(Integer.valueOf(3).byteValue());
					sBuffer.putLong(sessionId);
					sBuffer.putLong(data.sum);
				}
				sBuffer.flip();
				dc.send(sBuffer, sender);
				
			}
		}finally {
			dc.close();
		}
	}
	
	public static void usage() {
		System.out.println("Usage ServerLongSum port");
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
            new ServerLongSum(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
	}
}
