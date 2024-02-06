package fr.uge.exo2;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClientIdUpperCaseUDPBurst {

        private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
        private static final Charset UTF8 = StandardCharsets.UTF_8;
        private static final int BUFFER_SIZE = 1024;
        private final List<String> lines;
        private final int nbLines;
        private final String[] upperCaseLines; //
        private final int timeout;
        private final String outFilename;
        private final InetSocketAddress serverAddress;
        private final DatagramChannel dc;
        private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

        public static void usage() {
            System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
        }

        public ClientIdUpperCaseUDPBurst(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
            this.lines = lines;
            this.nbLines = lines.size();
            this.timeout = timeout;
            this.outFilename = outFilename;
            this.serverAddress = serverAddress;
            this.dc = DatagramChannel.open();
            dc.bind(null);
            this.upperCaseLines = new String[nbLines];
            this.answersLog = new AnswersLog(lines.size()); // TODO
        }

        private void senderThreadRun() {
        	var bb = ByteBuffer.allocate(BUFFER_SIZE);
        	while(!answersLog.allReceived()) {
        		for(var id : answersLog.notReceived()) {
        			try {
        				bb.clear();
            			bb.putLong(id);
            			bb.put(UTF8.encode(lines.get(id)));
            			bb.flip();
    					dc.send(bb, serverAddress);
    			}catch(AsynchronousCloseException e) {
    				logger.info("Channel Closed");
    				return;
    			} catch (IOException e) {
    				logger.log(Level.SEVERE,"IOException ",e);
    				return;
    			}
        		}
        	}
        }

        public void launch() throws IOException {
            Thread senderThread = Thread.ofPlatform().start(this::senderThreadRun);
            
				// TODO : body of the receiver thread
				var rbb = ByteBuffer.allocate(BUFFER_SIZE);
				for(;!answersLog.allReceived();) {
					try {
						rbb.clear();
						dc.receive(rbb);
						rbb.flip();
						if(rbb.remaining() < Long.BYTES) {
							return;
						}
						var id  = rbb.getLong();
						var msg = UTF8.decode(rbb).toString();
						answersLog.set(Long.valueOf(id).intValue());
						upperCaseLines[Long.valueOf(id).intValue()]=msg;
					} catch (AsynchronousCloseException e) {
						logger.info("Channel Closed ");
						return;
					} catch (IOException e) {
						logger.log(Level.SEVERE,"IOException ",e);
						return;
					}	
				}
            
				Files.write(Paths.get(outFilename),Arrays.asList(upperCaseLines), UTF8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        }

        public static void main(String[] args) throws IOException, InterruptedException {
            if (args.length !=5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.valueOf(args[2]);
            String host=args[3];
            int port = Integer.valueOf(args[4]);
            InetSocketAddress serverAddress = new InetSocketAddress(host,port);

            //Read all lines of inFilename opened in UTF-8
            List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
            //Create client with the parameters and launch it
            ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines,timeout,serverAddress,outFilename);
            client.launch();

        }

        private static class AnswersLog {
        	private final BitSet log;
        	private final int size;
        	private final Object lock = new Object();
        	public AnswersLog(int nblines) {
        		log = new BitSet(nblines);
        		size = nblines;
        	}
        	
        	public boolean allReceived() {
        		synchronized(lock) {
        			return log.cardinality()==size;
        		}
        	}
        	
        	public void set(int id) {
        		synchronized(lock) {
        			log.set(id);
        		}
        		
        	}
        	
        	public List<Integer> notReceived(){
        		synchronized(lock) {
        			return IntStream.range(0,size).filter(f-> !log.get(f)).boxed().collect(Collectors.toList());
        		}
        	}
        	
        }
    }


