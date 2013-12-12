package com.intellij.tasks.httpclient;

import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.util.net.HttpConfigurable;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class NewBaseRepositoryImpl extends BaseRepository {


  protected NewBaseRepositoryImpl() {
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
}
