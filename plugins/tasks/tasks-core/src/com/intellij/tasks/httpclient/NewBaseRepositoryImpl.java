package com.intellij.tasks.httpclient;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificatesManager;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This alternative base implementation of {@link com.intellij.tasks.impl.BaseRepository} should be used
 * for new connectors that use httpclient-4.x instead of legacy httpclient-3.1.
 *
 * @author Mikhail Golubev
 */
public abstract class NewBaseRepositoryImpl extends BaseRepository {
  private static final Logger LOG = Logger.getInstance(NewBaseRepositoryImpl.class);

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
      .setSslcontext(CertificatesManager.getInstance().getSslContext())
        // TODO: use custom one for additional certificate check
      //.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
      .setHostnameVerifier((X509HostnameVerifier)CertificatesManager.HOSTNAME_VERIFIER)
      .setDefaultCredentialsProvider(createCredentialsProvider());
    HttpRequestInterceptor interceptor = createRequestInterceptor();
    if (interceptor != null) {
      builder = builder.addInterceptorLast(interceptor);
    }
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
    HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    if (isUseHttpAuthentication()) {
      provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(getUsername(), getPassword()));
    }
    if (isUseProxy() && proxySettings.PROXY_AUTHENTICATION) {
      provider.setCredentials(new AuthScope(new HttpHost(proxySettings.PROXY_HOST)),
                              new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                                                              proxySettings.getPlainProxyPassword()));
    }
    return provider;
  }

  @NotNull
  protected RequestConfig createRequestConfig() {
    TaskSettings tasksSettings = TaskSettings.getInstance();
    HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    RequestConfig.Builder builder = RequestConfig.custom()
      .setConnectTimeout(3000)
      .setSocketTimeout(tasksSettings.CONNECTION_TIMEOUT);
    if (isUseProxy()) {
      HttpHost proxy = new HttpHost(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
      builder = builder.setProxy(proxy);
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
}
