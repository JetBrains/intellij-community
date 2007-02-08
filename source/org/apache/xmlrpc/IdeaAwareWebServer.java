package org.apache.xmlrpc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A minimal web server that uses IDEA built-in pool
 *
 * @author Maxim.Mossienko
 */
public class IdeaAwareWebServer extends WebServer
{
    private static ExecutorService threadPool = Executors.newFixedThreadPool(2, new ThreadFactory() {
      public Thread newThread(final Runnable r) {
        return new Thread(r, "WebServer thread pool");
      }
    });

  /**
     * Creates a web server at the specified port number and IP
     * address.
     */
    public IdeaAwareWebServer(int port, InetAddress addr, XmlRpcServer xmlrpc)
    {
        super(port, addr, xmlrpc);
    }

    /**
     *
     * @return
     */
    protected Runner getRunner()
    {
        return new MyRunner();
    }

    /**
     * Put <code>runner</code> back into {@link #threadpool}.
     *
     * @param runner The instance to reclaim.
     */
    void repoolRunner(Runner runner)
    {
    }

    /**
     * Responsible for handling client connections.
     */
    class MyRunner extends Runner
    {
        /**
         * Handles the client connection on <code>socket</code>.
         *
         * @param socket The source to read the client's request from.
         */
        public synchronized void handle(Socket socket) throws IOException
        {
            con = new Connection(socket);
            count = 0;

            try {
                  threadPool.submit(this);
            }
            catch (Exception e) {
              e.printStackTrace();
            }
        }

        /**
         * Delegates to <code>con.run()</code>.
         */
        public void run()
        {
            try {
                con.run();
            } finally {
                Thread.interrupted(); // reset interrupted status
            }
        }
    }
}
