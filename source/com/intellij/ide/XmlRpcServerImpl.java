/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import org.apache.xmlrpc.WebServer;
import org.jetbrains.annotations.NonNls;

/**
 * @author mike
 */
public class XmlRpcServerImpl implements XmlRpcServer, ApplicationComponent {
  public static final int PORT_NUMBER = 63342;
  private WebServer myWebServer;

  @NonNls
  public String getComponentName() {
    return "XmlRpcServer";
  }

  public void initComponent() {
    myWebServer = new WebServer(PORT_NUMBER);
    myWebServer.start();
  }

  public void disposeComponent() {
    myWebServer.shutdown();
  }

  public void addHandler(String name, Object handler) {
    myWebServer.addHandler(name, handler);
  }

  public void removeHandler(String name) {
    myWebServer.removeHandler(name);
  }
}
