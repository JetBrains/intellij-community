package com.jetbrains.python.console;

import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcServer;

/**
 * Web server to handle all the input requests by PydevConsoleCommunication
 * @author oleg
 */
public class PydevWebServer extends WebServer {
  public PydevWebServer(int port, final PydevConsoleCommunication communication) {
    super(port, null, createXmlRpcServer(communication));
  }

  private static XmlRpcServer createXmlRpcServer(final PydevConsoleCommunication communication) {
    final XmlRpcServer server = new XmlRpcServer();
    server.addHandler("$default", communication);
    return server;
  }
}
