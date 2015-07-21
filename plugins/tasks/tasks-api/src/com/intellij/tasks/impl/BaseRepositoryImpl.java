/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.tasks.impl;

import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Base class for HTTP-based repositories.
 *
 * @author Dmitry Avdeev
 */
public abstract class BaseRepositoryImpl extends BaseRepository {
  private final HttpClient myClient;

  protected BaseRepositoryImpl() {
    myClient = createClient();
  }

  protected BaseRepositoryImpl(TaskRepositoryType type) {
    super(type);
    myClient = createClient();
  }

  protected BaseRepositoryImpl(BaseRepositoryImpl other) {
    super(other);
    myClient = other.myClient;
  }

  protected static String encodeUrl(@NotNull String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  protected HttpClient getHttpClient() {
    return myClient;
  }

  private HttpClient createClient() {
    HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager());
    configureHttpClient(client);
    return client;
  }

  protected final void reconfigureClient() {
    synchronized (myClient) {
      configureHttpClient(myClient);
    }
  }

  protected void configureHttpClient(HttpClient client) {
    client.getParams().setConnectionManagerTimeout(3000);
    client.getParams().setSoTimeout(TaskSettings.getInstance().CONNECTION_TIMEOUT);
    if (isUseProxy()) {
      HttpConfigurable proxy = HttpConfigurable.getInstance();
      client.getHostConfiguration().setProxy(proxy.PROXY_HOST, proxy.PROXY_PORT);
      if (proxy.PROXY_AUTHENTICATION) {
        AuthScope authScope = new AuthScope(proxy.PROXY_HOST, proxy.PROXY_PORT);
        Credentials credentials = getCredentials(proxy.PROXY_LOGIN, proxy.getPlainProxyPassword(), proxy.PROXY_HOST);
        client.getState().setProxyCredentials(authScope, credentials);
      }
    }
    if (isUseHttpAuthentication()) {
      client.getParams().setCredentialCharset("UTF-8");
      client.getParams().setAuthenticationPreemptive(true);
      client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(getUsername(), getPassword()));
    }
    else {
      client.getState().clearCredentials();
      client.getParams().setAuthenticationPreemptive(false);
    }
  }

  @Nullable
  private static Credentials getCredentials(String login, String password, String host) {
    int domainIndex = login.indexOf("\\");
    if (domainIndex > 0) {
      // if the username is in the form "user\domain"
      // then use NTCredentials instead of UsernamePasswordCredentials
      String domain = login.substring(0, domainIndex);
      if (login.length() > domainIndex + 1) {
        String user = login.substring(domainIndex + 1);
        return new NTCredentials(user, password, host, domain);
      }
      else {
        return null;
      }
    }
    else {
      return new UsernamePasswordCredentials(login, password);
    }
  }

  protected void configureHttpMethod(HttpMethod method) {
  }

  public abstract static class HttpTestConnection<T extends HttpMethod> extends CancellableConnection {
    protected T myMethod;

    public HttpTestConnection(T method) {
      myMethod = method;
    }

    @Override
    protected void doTest() throws Exception {
      doTest(myMethod);
    }

    @Override
    public void cancel() {
      myMethod.abort();
    }

    protected abstract void doTest(T method) throws Exception;
  }

  @Override
  public void setUseProxy(boolean useProxy) {
    if (useProxy != isUseProxy()) {
      super.setUseProxy(useProxy);
      reconfigureClient();
    }
  }

  @Override
  public void setUseHttpAuthentication(boolean useHttpAuthentication) {
    if (useHttpAuthentication != isUseHttpAuthentication()) {
      super.setUseHttpAuthentication(useHttpAuthentication);
      reconfigureClient();
    }
  }

  @Override
  public void setPassword(String password) {
    if (!password.equals(getPassword())) {
      super.setPassword(password);
      reconfigureClient();
    }
  }

  @Override
  public void setUsername(String username) {
    if (!username.equals(getUsername())) {
      super.setUsername(username);
      reconfigureClient();
    }
  }
}
