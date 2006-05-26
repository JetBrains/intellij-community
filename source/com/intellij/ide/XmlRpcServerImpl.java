/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.xmlrpc.WebServer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;

/**
 * @author mike
 */
public class XmlRpcServerImpl implements XmlRpcServer, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.XmlRpcServerImpl");
  public static final int PORT_NUMBER = 63342;
  private WebServer myWebServer;
  @NonNls private static final String PROPERTY_RPC_PORT = "rpc.port";

  @NotNull
  @NonNls
  public String getComponentName() {
    return "XmlRpcServer";
  }

  public void initComponent() {
    if (!checkPort()) return;

    try {
      myWebServer = new WebServer(getPortNumber());
      myWebServer.start();
    }
    catch (Exception e) {
      LOG.error(e);
      myWebServer = null;
    }
  }

  public int getPortNumber() {
    return getPortImpl();
  }

  private static int getPortImpl() {
    if (System.getProperty(PROPERTY_RPC_PORT) != null) return Integer.parseInt(System.getProperty(PROPERTY_RPC_PORT));
    return PORT_NUMBER;
  }

  private static boolean checkPort() {
    ServerSocket socket = null;

    try {
      socket = new ServerSocket(getPortImpl());
    }
    catch (BindException e) {
      return false;
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException e) {
          LOG.error(e);
          return false;
        }
      }
    }

    return true;
  }

  public void disposeComponent() {
    if (myWebServer != null) {
      myWebServer.shutdown();
    }
  }

  public void addHandler(String name, Object handler) {
    if (myWebServer != null) {
      myWebServer.addHandler(name, handler);
    }
    else {
      LOG.info("Handler not registered because XML-RPC server is not running");
    }
  }

  public void removeHandler(String name) {
    if (myWebServer != null) {
      myWebServer.removeHandler(name);
    }
  }
}
