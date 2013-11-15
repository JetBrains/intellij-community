package com.intellij.tasks.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.ssl.CertificatesManager;
import com.intellij.util.net.HttpConfigurable;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Base class for HTTP-based repositories.
 *
 * @author Dmitry Avdeev
 */
public abstract class BaseRepositoryImpl extends BaseRepository {
  public static final String EASY_HTTPS = "easyhttps";

  private static final Logger LOG = Logger.getInstance(BaseRepositoryImpl.class);

  static {
    try {
      SSLContext context = CertificatesManager.createDefault().createSslContext();
      SSLContext.setDefault(context);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
    // Protocol.registerProtocol("https", CertificatesManager.createDefault().createProtocol());
    Protocol.registerProtocol(EASY_HTTPS, new Protocol(EASY_HTTPS, (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443));
  }

  protected BaseRepositoryImpl() {
  }

  protected BaseRepositoryImpl(TaskRepositoryType type) {
    super(type);
  }

  protected BaseRepositoryImpl(BaseRepository other) {
    super(other);
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
    HttpClient client = new HttpClient();
    configureHttpClient(client);
    return client;
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
