package com.intellij.tasks.impl;

import com.intellij.tasks.TaskRepositoryType;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for HTTP-based repositories.
 *
 * @author Dmitry Avdeev
 */
public abstract class BaseRepositoryImpl extends BaseRepository {
  public static final String EASY_HTTPS = "easyhttps";

  static {
    Protocol.registerProtocol(EASY_HTTPS, new Protocol("https", (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443));
  }

  private static final Pattern PATTERN = Pattern.compile("[A-Z]+\\-\\d+");

  protected BaseRepositoryImpl() {
  }

  protected BaseRepositoryImpl(TaskRepositoryType type) {
    super(type);
  }

  protected BaseRepositoryImpl(BaseRepository other) {
    super(other);
  }

  @Nullable
  public String extractId(String taskName) {
    Matcher matcher = PATTERN.matcher(taskName);
    return matcher.find() ? matcher.group() : null;
  }

  protected static String encodeUrl(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  protected HttpClient getHttpClient() {
    HttpClient client = new HttpClient();
    configureHttpClient(client);
    return client;
  }

  protected void configureHttpClient(HttpClient client) {
    client.getParams().setConnectionManagerTimeout(3000);
    client.getParams().setSoTimeout(3000);
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
      client.getParams().setAuthenticationPreemptive(true);
      client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(getUsername(), getPassword()));
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
      } else {
        return null;
      }
    }
    else {
      return new UsernamePasswordCredentials(login, password);
    }
  }

  protected void configureHttpMethod(HttpMethod method) {}

  public abstract class HttpTestConnection<T extends HttpMethod> extends CancellableConnection {

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
}
