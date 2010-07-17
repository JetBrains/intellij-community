package com.jetbrains.python.console;

import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpcHandlerMapping;
import org.apache.xmlrpc.XmlRpcServer;

/**
 * Web server to handle all the input requests by PydevConsoleCommunication
 * @author oleg
 */
public class PydevWebServer extends WebServer {
  public PydevWebServer(int port, final PydevConsoleCommunication communication) {
    super(port, null, new XmlRpcServer(){
      final XmlRpcHandlerMapping xmlRpcHandlerMapping = new XmlRpcHandlerMapping() {
        public Object getHandler(String handlerName) throws Exception {
          return communication;
        }
      };
      @Override
      public XmlRpcHandlerMapping getHandlerMapping() {
        return xmlRpcHandlerMapping;
      }
    });
  }
}
