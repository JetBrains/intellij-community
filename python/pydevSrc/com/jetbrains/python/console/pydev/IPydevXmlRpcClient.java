// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.console.pydev;

import org.apache.xmlrpc.XmlRpcException;


/**
 * Interface that determines what's needed from the xml-rpc server.
 *
 * @author Fabio
 */
public interface IPydevXmlRpcClient {

    /**
     * @param command the command to be executed in the server
     * @param args the arguments passed to the command
     * @return the result from executing the command
     *
     */
    Object execute(String command, Object[] args) throws XmlRpcException;

    Object execute(String command, Object[] args, long timeoutMillis) throws XmlRpcException;

}
