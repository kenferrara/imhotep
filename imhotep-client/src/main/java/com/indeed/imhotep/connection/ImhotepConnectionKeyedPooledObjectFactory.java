package com.indeed.imhotep.connection;

import com.indeed.imhotep.client.Host;
import com.indeed.util.core.io.Closeables2;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author xweng
 */
public class ImhotepConnectionKeyedPooledObjectFactory implements KeyedPooledObjectFactory<Host, Socket> {
    private static final Logger logger = Logger.getLogger(ImhotepConnectionKeyedPooledObjectFactory.class);

    // We hope the client side time out at first, and then the server socket received EOFException and close it self.
    // The socket time out of server side is 60 seconds, so here we set is as 45 seconds
    private static final int SOCKET_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(45);

    // keyedObjectPool doesn't handle the timeout during makeObject, we have to specify it in case of connection block
    private static final int SOCKET_CONNECTING_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);

    @Override
    public PooledObject<Socket> makeObject(final Host host) throws IOException {
        final Socket socket = new Socket();
        final SocketAddress endpoint = new InetSocketAddress(host.getHostname(), host.getPort());
        socket.connect(endpoint, SOCKET_CONNECTING_TIMEOUT_MILLIS);

        socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
        socket.setReceiveBufferSize(65536);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);

        return new DefaultPooledObject<>(socket);
    }

    @Override
    public void destroyObject(final Host host, final PooledObject<Socket> pooledObject) throws IOException {
        final Socket socket = pooledObject.getObject();
        try {
            socket.shutdownInput();
            // TODO: read remained data from socket in case of Connection Reset Exception
        } finally {
            Closeables2.closeQuietly(socket, logger);
        }
    }

    @Override
    public boolean validateObject(final Host host, final PooledObject<Socket> pooledObject) {
        if (pooledObject.getIdleTimeMillis() >= SOCKET_TIMEOUT_MILLIS) {
            return false;
        }

        final Socket socket = pooledObject.getObject();
        if (!socket.isConnected() || socket.isClosed()) {
            return false;
        }
        try {
            // minimum check if there is remained data in the socket stream
            return socket.getInputStream().available() == 0;
        } catch (final Exception e) {
            return false;
        }
    }

    @Override
    public void activateObject(final Host host, final PooledObject<Socket> pooledObject) {
    }

    @Override
    public void passivateObject(final Host host, final PooledObject<Socket> pooledObject) {
    }
}
