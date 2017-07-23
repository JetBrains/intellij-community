package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.ConfirmingTrustManager;
import com.jetbrains.edu.learning.StudySerializationUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class EduStepicClient {
  private static final Logger LOG = Logger.getInstance(EduStepicClient.class.getName());
  private static CloseableHttpClient ourClient;

  private EduStepicClient() {
  }

  @NotNull
  public static CloseableHttpClient getHttpClient() {
    if (ourClient == null) {
      initializeClient();
    }
    return ourClient;
  }

  public static <T> T getFromStepic(String link, final Class<T> container) throws IOException {
    return getFromStepic(link, container, getHttpClient());
  }

  static <T> T getFromStepic(String link, final Class<T> container, @NotNull final CloseableHttpClient client) throws IOException {
    if (!link.startsWith("/")) link = "/" + link;
    final HttpGet request = new HttpGet(EduStepicNames.STEPIC_API_URL + link);

    final CloseableHttpResponse response = client.execute(request);
    final StatusLine statusLine = response.getStatusLine();
    final HttpEntity responseEntity = response.getEntity();
    final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
    EntityUtils.consume(responseEntity);
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      throw new IOException("Stepic returned non 200 status code " + responseString);
    }
    return deserializeStepicResponse(container, responseString);
  }

  static <T> T deserializeStepicResponse(Class<T> container, String responseString) {
    Gson gson =
      new GsonBuilder().registerTypeAdapter(StepicWrappers.StepOptions.class, new StudySerializationUtils.Json.StepicStepOptionsAdapter()).
        setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").
        setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    return gson.fromJson(responseString, container);
  }

  private static void initializeClient() {
    if (ourClient == null) {
      final HttpClientBuilder builder = getBuilder();
      ourClient = builder.build();
    }
  }

  @NotNull
  static HttpClientBuilder getBuilder() {
    final HttpClientBuilder builder = HttpClients.custom().setSSLContext(CertificateManager.getInstance().getSslContext()).
      setMaxConnPerRoute(100000).setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);

    final HttpConfigurable proxyConfigurable = HttpConfigurable.getInstance();
    final List<Proxy> proxies = proxyConfigurable.getOnlyBySettingsSelector().select(URI.create(EduStepicNames.STEPIC_URL));
    final InetSocketAddress address = proxies.size() > 0 ? (InetSocketAddress)proxies.get(0).address() : null;
    if (address != null) {
      builder.setProxy(new HttpHost(address.getHostName(), address.getPort()));
    }
    final ConfirmingTrustManager trustManager = CertificateManager.getInstance().getTrustManager();
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
      builder.setSSLContext(sslContext);
    }
    catch (NoSuchAlgorithmException | KeyManagementException e) {
      LOG.error(e.getMessage());
    }
    return builder;
  }

  static boolean isTokenUpToDate(@NotNull String token) {
    if (token.isEmpty()) return false;

    final List<BasicHeader> headers = new ArrayList<>();
    headers.add(new BasicHeader("Authorization", "Bearer " + token));
    headers.add(new BasicHeader("Content-type", EduStepicNames.CONTENT_TYPE_APP_JSON));
    CloseableHttpClient httpClient = getBuilder().setDefaultHeaders(headers).build();

    try {
      final StepicWrappers.AuthorWrapper wrapper =
        getFromStepic(EduStepicNames.CURRENT_USER, StepicWrappers.AuthorWrapper.class, httpClient);
      if (wrapper != null && !wrapper.users.isEmpty()) {
        StepicUser user = wrapper.users.get(0);
        return user != null && !user.isGuest();
      }
      else {
        throw new IOException(wrapper == null ? "Got a null current user" : "Got an empty wrapper");
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }
}
