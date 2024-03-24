package fr.upem.net.tcp.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class FakeHTTPServer {
    private final ServerSocketChannel ssc;
    private final int port;
    private final ByteBuffer content;
    private final Thread thread;

    public FakeHTTPServer(String s, int max) throws IOException {
        ssc = ServerSocketChannel.open();
        ssc.bind(null);
        var address = (InetSocketAddress) ssc.getLocalAddress();
        port = address.getPort();
        content = ByteBuffer.wrap(s.getBytes("UTF-8"));
        thread = new Thread(() -> {
            SocketChannel sc = null;
            try {
                sc = ssc.accept();
                while (!Thread.interrupted() && content.hasRemaining()) {
                    var oldlimit = content.limit();
                    content.limit(Math.min(content.position() + max, oldlimit));
                    sc.write(content);
                    Thread.sleep(100);
                    content.limit(oldlimit);
                }
            } catch (Exception e) {
                //
            } finally {
                try {
                    if (sc != null) {
                        sc.close();
                    }
                    ssc.close();
                } catch (Exception e) {
                    //
                }
            }
        });
    }

    public FakeHTTPServer(InputStream in) throws IOException {
        ssc = ServerSocketChannel.open();
        ssc.bind(null);
        var address = (InetSocketAddress) ssc.getLocalAddress();
        port = address.getPort();
        content = null;
        this.thread = new Thread(() -> {
            SocketChannel sc = null;
            try {
                sc = ssc.accept();
                byte[] buffer = new byte[100];
                var byteBuffer = ByteBuffer.wrap(buffer);
                var read = 0;
                while (!Thread.interrupted() && (read = in.read(buffer)) != -1) {
                    byteBuffer.clear();
                    byteBuffer.limit(read);
                    sc.write(byteBuffer);
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                //
            } finally {
                try {
                    if (sc != null) {
                        sc.close();
                    }
                    ssc.close();
                } catch (Exception e) {
                    //
                }
            }
        });
    }

    public void serve() {
        thread.start();
    }

    public void shutdown() {
        thread.interrupt();
    }

    public int getPort() {
        return port;
    }
}