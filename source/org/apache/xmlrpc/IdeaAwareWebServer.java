package org.apache.xmlrpc;

import org.apache.commons.codec.binary.Base64;

import java.net.*;
import java.util.Vector;
import java.util.Stack;
import java.util.EmptyStackException;
import java.util.StringTokenizer;
import java.io.*;
import java.lang.reflect.Method;

/**
 * A minimal web server that uses IDEA built-in pool
 *
 * @author Maxim.Mossienko
 */
public class IdeaAwareWebServer extends WebServer
{
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
                  // Attempt to execute on pooled thread
                  final Class<?> aClass = Class.forName("com.intellij.openapi.application.ApplicationManager");
                  final Method getApplicationMethod = aClass.getMethod("getApplication");
                  final Object application = getApplicationMethod.invoke(null);
                  final Method executeOnPooledThreadMethod = application.getClass().getMethod("executeOnPooledThread", Runnable.class);
                  executeOnPooledThreadMethod.invoke(application, this);
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
