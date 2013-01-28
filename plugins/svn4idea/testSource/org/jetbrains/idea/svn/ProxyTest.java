/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

//import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/20/13
 * Time: 10:51 PM
 */
public class ProxyTest {

  private ProxySelector myDefault;

  // to be run with java.net.useSystemProxies or without
  // result: hangs if used with -Djava.net.useSystemProxies=true
  @Test
  public void testSimpleSvnkitConnection() throws Exception {
    svnkitCall();
  }

  private void svnkitCall() throws IOException, SVNException {
    final String url = "msdc.labs.intellij.net";
    final InetAddress name = InetAddress.getByName(url);
    setAuthenticator();
    final Socket socket = SVNSocketFactory.createPlainSocket(name.getHostAddress(), 80, 120000, 120000, null);
    System.out.println("connected: " + socket.isConnected());
  }

  private void setAuthenticator() {
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        System.out.println("Authentication asked, requesting url: " + getRequestingHost());
        return new PasswordAuthentication("user1", "fg3W9".toCharArray());
      }
    });
  }

  // to be run with java.net.useSystemProxies or without
  // result: false anyway
  @Test
  public void testSimpleHttpConnection() throws Exception {
    System.out.println("java.net.useSystemProxies: " + System.getProperty("java.net.useSystemProxies"));
    httpCall();
  }

  private void httpCall() throws IOException {
    final String url = "http://msdc.labs.intellij.net";
    HttpURLConnection connection = null;
    try {
      URL url1 = new URL(url);
      connection = (HttpURLConnection)url1.openConnection();
      System.out.println("using proxy: " + connection.usingProxy());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  // result: properties does not affect
  @Test
  public void testSvnkitWithProxyInProperties() throws Exception {
    try {
      System.setProperty("proxySet", "true");
      System.setProperty("http.proxyHost", "proxy-auth-test.labs.intellij.net");
      System.setProperty("http.proxyPort", Integer.toString (3128));
      svnkitCall();
    } finally {
      System.clearProperty("proxySet");
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
    }
  }

  // result: properties does not affect
  @Test
  public void testHttpWithProxyInProperties() throws Exception {
    try {
      System.setProperty("proxySet", "true");
      System.setProperty("http.proxyHost", "proxy-auth-test.labs.intellij.net");
      System.setProperty("http.proxyPort", Integer.toString (3128));
      httpCall();
    } finally {
      System.clearProperty("proxySet");
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
    }
  }

  // result: exception!
  @Test
  public void testSvnkitWithProxySelector() throws Exception {
    try {
      defaultProxy();
      svnkitCall();
    } finally {
      ProxySelector.setDefault(myDefault);
    }
  }

  // result: does not affect
  @Test
  public void testHttpWithProxySelector() throws Exception {
    try {
      defaultProxy();
      httpCall();
    } finally {
      ProxySelector.setDefault(myDefault);
    }
  }

  private void defaultProxy() {
    myDefault = ProxySelector.getDefault();
    ProxySelector.setDefault(new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy-auth-test.labs.intellij.net", 3128)));
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        System.out.println("failed for url: " + uri.getHost());
      }
    });
  }

  private @interface Test {}
}
