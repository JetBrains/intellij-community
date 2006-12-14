package org.apache.xmlrpc;

import java.io.InputStream;

/**
 * XmlRpcServer that does not pool processors
 *
 * @author Maxim.Mossienko
 */
public class IdeaAwareXmlRpcServer extends XmlRpcServer {

    public IdeaAwareXmlRpcServer()
    {

    }

    /**
     * Parse the request and execute the handler method, if one is
     * found. If the invoked handler is AuthenticatedXmlRpcHandler,
     * use the credentials to authenticate the user. Context information
     * is passed to the worker, and may be passed to the request handler.
     */
    public byte[] execute(InputStream is, XmlRpcContext context)
    {
        XmlRpcWorker worker = getWorker();
        return worker.execute(is, context);
    }

    /**
     * Hands out pooled workers.
     *
     * @return A worker (never <code>null</code>).
     * @throws RuntimeException If the server exceeds its maximum
     * number of allowed requests.
     */
    protected XmlRpcWorker getWorker()
    {
        return new XmlRpcWorker(getHandlerMapping());
    }
}
