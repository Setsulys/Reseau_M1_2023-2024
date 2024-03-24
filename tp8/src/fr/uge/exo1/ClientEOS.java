package fr.uge.exo1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    public static final int BUFFER_SIZE = 1024;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());
    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - stores the bufferSize first bytes of server response 
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @param bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server
     *         response
     * @throws IOException
     */

    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
    	SocketChannel sc = SocketChannel.open();
    	sc.connect(server);
    	ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
    	sc.write(UTF8_CHARSET.encode(request));
    	sc.shutdownOutput();
    	
    	
    	buffer.clear();
    	int read =0;
    	while(buffer.hasRemaining() && read !=-1) {
    		read = sc.read(buffer);
    	}
    	
    	buffer.flip();
        return UTF8_CHARSET.decode(buffer).toString();
    }

    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - reads and stores all bytes from server until read-channel is closed 
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @return the UTF8 string corresponding the full response of the server
     * @throws IOException
     */

    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
    	SocketChannel sc = SocketChannel.open();
    	sc.connect(server);
    	ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    	sc.write(UTF8_CHARSET.encode(request));
    	sc.shutdownOutput();
    	
    	while(readFully(sc, buffer)) {
    		if(!buffer.hasRemaining()) {
    			buffer = storing(buffer);
    		}
    	}
    	buffer.flip();
        return UTF8_CHARSET.decode(buffer).toString();
    }
    
    public static ByteBuffer storing(ByteBuffer buffer) {
    	ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
    	return newBuffer.put(buffer.flip());
    }

    /**
     * Fill the workspace of the Bytebuffer with bytes read from sc.
     *
     * @param sc
     * @param buffer
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException
     */
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        // TODO
    	int read=0;
    	while(buffer.hasRemaining() && read !=-1) {
    		read = sc.read(buffer);
    	}
        return read !=-1;
    }

    public static void main(String[] args) throws IOException {
        var google = new InetSocketAddress("www.google.fr", 80);
        //System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
        System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}
