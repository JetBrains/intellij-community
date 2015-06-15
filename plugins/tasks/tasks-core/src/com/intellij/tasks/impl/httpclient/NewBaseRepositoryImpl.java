package com.intellij.tasks.impl.httpclient;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.net.IdeHttpClientHelpers;
import com.intellij.util.net.ssl.CertificateManager;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * This alternative base implementation of {@link com.intellij.tasks.impl.BaseRepository} should be used
 * for new connectors that use httpclient-4.x instead of legacy httpclient-3.1.
 *
 * @author Mikhail Golubev
 */
public abstract class NewBaseRepositoryImpl extends BaseRepository {
  private static final AuthScope BASIC_AUTH_SCOPE =
    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthSchemes.BASIC);
  // Provides preemptive authentication in HttpClient 4.x
  // see http://stackoverflow.com/questions/2014700/preemptive-basic-authentication-with-apache-httpclient-4
  private static final HttpRequestInterceptor PREEMPTIVE_BASIC_AUTH = new PreemptiveBasicAuthInterceptor();

  /**
   * Serialization constructor
   */
  protected NewBaseRepositoryImpl() {
    // empty
  }

  protected NewBaseRepositoryImpl(TaskRepositoryType type) {
    super(type);
  }

  protected NewBaseRepositoryImpl(BaseRepository other) {
    super(other);
  }

  @NotNull
  protected HttpClient getHttpClient() {
    HttpClientBuilder builder = HttpClients.custom()
      .setDefaultRequestConfig(createRequestConfig())
      .setSslcontext(CertificateManager.getInstance().getSslContext())
        // TODO: use custom one for additional certificate check
        //.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
      .setHostnameVerifier((X509HostnameVerifier)CertificateManager.HOSTNAME_VERIFIER)
      .setDefaultCredentialsProvider(createCredentialsProvider())
      .addInterceptorFirst(PREEMPTIVE_BASIC_AUTH)
      .addInterceptorLast(createRequestInterceptor());
    return builder.build();
  }

  /**
   * Custom request interceptor can be used for modifying outgoing requests. One possible usage is to
   * add specific header to each request according to authentication scheme used.
   *
   * @return specific request interceptor or null by default
   */
  @Nullable
  protected HttpRequestInterceptor createRequestInterceptor() {
    return null;
  }

  @NotNull
  private CredentialsProvider createCredentialsProvider() {
    CredentialsProvider provider = new BasicCredentialsProvider();
    // Basic authentication
    if (isUseHttpAuthentication()) {
      provider.setCredentials(BASIC_AUTH_SCOPE, new UsernamePasswordCredentials(getUsername(), getPassword()));
    }
    // Proxy authentication
    if (isUseProxy()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyCredentialsForUrlIfEnabled(provider, getUrl());
    }

    return provider;
  }

  @NotNull
  protected RequestConfig createRequestConfig() {
    TaskSettings tasksSettings = TaskSettings.getInstance();
    RequestConfig.Builder builder = RequestConfig.custom()
      .setConnectTimeout(3000)
      .setSocketTimeout(tasksSettings.CONNECTION_TIMEOUT);
    if (isUseProxy()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyForUrlIfEnabled(builder, getUrl());
    }

    return builder.build();
  }

  /**
   * Return server's REST API path prefix, e.g. {@code /rest/api/latest} for JIRA or {@code /api/v3/} for Gitlab.
   * This value will be used in {@link #getRestApiUrl(Object...)}
   *
   * @return server's REST API path prefix
   */
  @NotNull
  public String getRestApiPathPrefix() {
    return "";
  }

  /**
   * Build URL using {@link #getUrl()}, {@link #getRestApiPathPrefix()}} and specified path components.
   * <p/>
   * Individual path components will should not contain leading or trailing slashes. Empty or null components
   * will be omitted. Each components is converted to string using its {@link Object#toString()} method and url encoded, so
   * numeric IDs can be used as well. Returned URL doesn't contain trailing '/', because it's not compatible with some services.
   *
   * @return described URL
   */
  @NotNull
  public String getRestApiUrl(@NotNull Object... parts) {
    StringBuilder builder = new StringBuilder(getUrl());
    builder.append(getRestApiPathPrefix());
    if (builder.charAt(builder.length() - 1) == '/') {
      builder.deleteCharAt(builder.length() - 1);
    }
    for (Object part : parts) {
      if (part == null || part.equals("")) {
        continue;
      }
      builder.append('/').append(TaskUtil.encodeUrl(String.valueOf(part)));
    }
    return builder.toString();
  }

  private static class PreemptiveBasicAuthInterceptor implements HttpRequestInterceptor {
    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
      final CredentialsProvider provider = (CredentialsProvider)context.getAttribute(HttpClientContext.CREDS_PROVIDER);
      final Credentials credentials = provider.getCredentials(BASIC_AUTH_SCOPE);
      if (credentials != null) {
        request.addHeader(new BasicScheme(CharsetToolkit.UTF8_CHARSET).authenticate(credentials, request, context));
      }
      final HttpHost proxyHost = ((HttpRoute)context.getAttribute(HttpClientContext.HTTP_ROUTE)).getProxyHost();
      if (proxyHost != null) {
        final Credentials proxyCredentials = provider.getCredentials(new AuthScope(proxyHost));
        if (proxyCredentials != null) {
          request.addHeader(BasicScheme.authenticate(proxyCredentials, CharsetToolkit.UTF8, true));
        }
      }
    }
  }

  public class HttpTestConnection extends CancellableConnection {

    // Request can be changed during test
    protected volatile HttpRequestBase myCurrentRequest;

    public HttpTestConnection(@NotNull HttpRequestBase request) {
      myCurrentRequest = request;
    }

    @Override
    protected void doTest() throws Exception {
      try {
        test();
      }
      catch (IOException e) {
        // Depending on request state AbstractExecutionAwareRequest.abort() can cause either
        // * RequestAbortedException if connection was not yet leased
        // * InterruptedIOException before reading response
        // * SocketException("Socket closed") during reading response
        // However in all cases 'aborted' flag should be properly set
        if (!myCurrentRequest.isAborted()) {
          throw e;
        }
      }
    }

    protected void test() throws Exception {
      HttpResponse response = getHttpClient().execute(myCurrentRequest);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
        throw RequestFailedException.forStatusCode(statusLine.getStatusCode(), statusLine.getReasonPhrase());
      }
    }

    @Override
    public void cancel() {
      myCurrentRequest.abort();
    }
  }
}
