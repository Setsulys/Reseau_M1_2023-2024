package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;

public class HTTPReader {

	private final Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final SocketChannel sc;
	private final ByteBuffer buffer;

	public HTTPReader(SocketChannel sc, ByteBuffer buffer) {
		this.sc = sc;
		this.buffer = buffer;
	}

	/**
	 * @return The ASCII string terminated by CRLF without the CRLF
	 *         <p>
	 *         The method assume that buffer is in write mode and leaves it in
	 *         write mode The method process the data from the buffer and if necessary
	 *         will read more data from the socket.
	 * @throws IOException HTTPException if the connection is closed before a line
	 *                     could be read
	 */
	public String readLineCRLF() throws IOException {
		// TODO
		boolean finished = false;
		var builder = new StringBuilder();
		boolean lastCRLF=false;
		buffer.flip();
		try {
			while(!finished) {
				while(buffer.hasRemaining()) {
					var currentChar = (char) buffer.get();
					builder.append(currentChar);
					if(lastCRLF && currentChar=='\n') {
						finished=true;
						return builder.toString().replace("\r\n", "");
					}
					lastCRLF = (currentChar =='\r');
				}
				buffer.compact();
				if(sc.read(buffer)==-1) {
					throw new HTTPException();
				}
				buffer.flip();
			}
		}finally {
			buffer.compact();
		}
		return builder.toString().replace("\r\n", "");
	}

	/**
	 * @return The HTTPHeader object corresponding to the header read
	 * @throws IOException HTTPException if the connection is closed before a header
	 *                     could be read or if the header is ill-formed
	 */
	public HTTPHeader readHeader() throws IOException {
		// TODO
		var response =readLineCRLF();
		var fields = new HashMap<String,String>();
		String line;
		for(;;) {
			line = readLineCRLF();
			if(line.equals("")) {
				break;
			}
			var split = line.split(":",2);
			if(split.length!=2) {
				throw new HTTPException();
			}
			fields.merge(split[0], split[1], (k,v) -> v+";"+split[1]);
		}
		return HTTPHeader.create(response, fields);
	}

	/**
	 * @param size 
	 * @return a ByteBuffer in write mode containing size bytes read on the socket
	 *         <p>
	 *         The method assume that buffer is in write mode and leaves it in
	 *         write mode The method process the data from the buffer and if necessary
	 *         will read more data from the socket.
	 * @throws IOException HTTPException is the connection is closed before all
	 *                     bytes could be read
	 */
	public ByteBuffer readBytes(int size) throws IOException {
		ByteBuffer tmp= ByteBuffer.allocate(size);
		try {
			while(size!=0) {
				buffer.flip();
				while(buffer.hasRemaining() && size !=0) {
					if(buffer.remaining()>=size) {
						var oldLimit = buffer.limit();
						buffer.limit(size);
						tmp.put(buffer);
						buffer.limit(oldLimit);
						size=0;
						return tmp;
					}
					else {
						tmp.put(buffer.get());
						size--;
						if(size ==0) {
							return tmp;
						}
					}
				}
				buffer.clear();
				if(sc.read(buffer)==-1){
					throw new HTTPException();
				}

			}
		}finally {
			buffer.compact();
		}
		return tmp;
	}

	/**
	 * @return a ByteBuffer in write-mode containing a content read in chunks mode
	 * @throws IOException HTTPException if the connection is closed before the end
	 *                     of the chunks if chunks are ill-formed
	 */

	public ByteBuffer readChunks() throws IOException {
		// TODO
		
		return null;
	}

	public static void main(String[] args) throws IOException {
		var charsetASCII = Charset.forName("ASCII");
		var request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
		var sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		sc.write(charsetASCII.encode(request));
		var buffer = ByteBuffer.allocate(50);
		var reader = new HTTPReader(sc, buffer);
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		sc.close();

		buffer = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		reader = new HTTPReader(sc, buffer);
		sc.write(charsetASCII.encode(request));
		System.out.println(reader.readHeader());
		sc.close();

		buffer = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("igm.univ-mlv.fr", 80));
		request = "GET /coursprogreseau/ HTTP/1.1\r\n" + "Host: igm.univ-mlv.fr\r\n" + "\r\n";
		reader = new HTTPReader(sc, buffer);
		sc.write(charsetASCII.encode(request));
		var header = reader.readHeader();
		System.out.println(header);
		var content = reader.readBytes(header.getContentLength());
		content.flip();
		System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
		sc.close();

		buffer = ByteBuffer.allocate(50);
		request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
		reader = new HTTPReader(sc, buffer);
		sc.write(charsetASCII.encode(request));
		header = reader.readHeader();
		System.out.println(header);
		content = reader.readChunks();
		content.flip();
		System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
		sc.close();
	}
}
