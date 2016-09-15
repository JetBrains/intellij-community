package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.ssl.CertificateManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class EduStepicAuthorizedClient {
  private static final Logger LOG = Logger.getInstance(EduStepicAuthorizedClient.class.getName());
  private static final String ourClientId = "hUCWcq3hZHCmz0DKrDtwOWITLcYutzot7p4n59vU";
  private static CloseableHttpClient ourClient;

  private EduStepicAuthorizedClient() {
  }

  @NotNull
  public static CloseableHttpClient getHttpClient(@NotNull final Project project) {
    if (ourClient != null) {
      return ourClient;
    }
    ourClient = initializeClient(project);
    if (ourClient == null) {
      if (login(project)) {
        ourClient = initializeClient(project);
      }
    }
    return ourClient;
  }

  public static <T> T getFromStepic(String link, final Class<T> container, @NotNull final Project project) throws IOException {
    return EduStepicClient.getFromStepic(link, container, getHttpClient(project));
  }

  @Nullable
  private static CloseableHttpClient initializeClient(@NotNull final Project project) {
    final List<BasicHeader> headers = new ArrayList<>();
    final StepicUser currentUser = StudyTaskManager.getInstance(project).getUser();
    final String accessToken = currentUser.getAccessToken();
    if (accessToken != null && !accessToken.isEmpty()) {
      headers.add(new BasicHeader("Authorization", "Bearer " + accessToken));
      headers.add(new BasicHeader("Content-type", EduStepicNames.CONTENT_TYPE_APP_JSON));
      return getBuilder().setDefaultHeaders(headers).build();
    }
    return null;
  }

  @NotNull
  private static TrustManager[] trustAllCerts() {
    // Create a trust manager that does not validate certificate for this connection
    return new TrustManager[]{new X509TrustManager() {
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }

      public void checkClientTrusted(X509Certificate[] certs, String authType) {
      }

      public void checkServerTrusted(X509Certificate[] certs, String authType) {
      }
    }};
  }

  @NotNull
  private static HttpClientBuilder getBuilder() {
    final HttpClientBuilder builder = HttpClients.custom().setSslcontext(CertificateManager.getInstance().getSslContext()).
      setMaxConnPerRoute(100000).setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);

    final HttpConfigurable proxyConfigurable = HttpConfigurable.getInstance();
    final List<Proxy> proxies = proxyConfigurable.getOnlyBySettingsSelector().select(URI.create(EduStepicNames.STEPIC_URL));
    final InetSocketAddress address = proxies.size() > 0 ? (InetSocketAddress)proxies.get(0).address() : null;
    if (address != null) {
      builder.setProxy(new HttpHost(address.getHostName(), address.getPort()));
    }
    final TrustManager[] trustAllCerts = trustAllCerts();
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new SecureRandom());
      builder.setSslcontext(sslContext);
    }
    catch (NoSuchAlgorithmException | KeyManagementException e) {
      LOG.error(e.getMessage());
    }
    return builder;
  }

  private static boolean login(@NotNull final Project project) {
    final StepicUser user = StudyTaskManager.getInstance(project).getUser();
    final String login =  user.getEmail();
    final String refreshToken = user.getRefreshToken();
    if (StringUtil.isEmptyOrSpaces(login)) {
      return showLoginDialog();
    }
    else {
      if (StringUtil.isNotEmpty(refreshToken)) {
        final StepicWrappers.TokenInfo tokenInfo = login(refreshToken);
        user.setupTokenInfo(tokenInfo);
      }
      else {
        final StepicUser stepicUser = login(login, user.getPassword());
        if (stepicUser == null) {
          return showLoginDialog();
        }
        else {
          StudyTaskManager.getInstance(project).setUser(stepicUser);
        }
      }
    }
    return true;
  }

  private static boolean showLoginDialog() {
    final boolean[] logged = {false};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final LoginDialog dialog = new LoginDialog();
      dialog.show();
      logged[0] = dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE;
    });
    return logged[0];
  }

  public static StepicUser login(@NotNull final String email, @NotNull final String password) {
    final List<NameValuePair> parameters = new ArrayList<>();
    if (password.isEmpty()) return null;
    parameters.add(new BasicNameValuePair("client_id", ourClientId));
    parameters.add(new BasicNameValuePair("grant_type", "password"));
    parameters.add(new BasicNameValuePair("username", email));
    parameters.add(new BasicNameValuePair("password", password));

    final StepicWrappers.TokenInfo tokenInfo = postCredentials(parameters);

    final StepicUser user = new StepicUser(email, password);
    final StepicUser currentUser = getCurrentUser();
    if (currentUser != null) {
      user.setId(currentUser.getId());
    }
    user.setupTokenInfo(tokenInfo);
    return user;
  }

  private static StepicWrappers.TokenInfo login(@NotNull final String refreshToken) {
    final List<NameValuePair> parameters = new ArrayList<>();

    if (refreshToken.isEmpty()) return null;
    parameters.add(new BasicNameValuePair("client_id", ourClientId));
    parameters.add(new BasicNameValuePair("content-type", "application/json"));
    parameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
    parameters.add(new BasicNameValuePair("refresh_token", refreshToken));

    final StepicWrappers.TokenInfo tokenInfo = postCredentials(parameters);
    return tokenInfo;
  }

  @Nullable
  static StepicUser getCurrentUser() {
    try {
      final StepicWrappers.AuthorWrapper wrapper = EduStepicClient.getFromStepic(EduStepicNames.CURRENT_USER,
                                                                                    StepicWrappers.AuthorWrapper.class);
      if (wrapper != null && !wrapper.users.isEmpty()) {
        return wrapper.users.get(0);
      }
    }
    catch (IOException e) {
      LOG.warn("Couldn't get author info");
    }
    return null;
  }

  @Nullable
  private static StepicWrappers.TokenInfo postCredentials(@NotNull final List<NameValuePair> parameters) {
    final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    final HttpPost request = new HttpPost(EduStepicNames.TOKEN_URL);
    request.setEntity(new UrlEncodedFormEntity(parameters, Consts.UTF_8));

    try {
      final CloseableHttpClient client = EduStepicClient.getHttpClient();
      final CloseableHttpResponse response = client.execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      EntityUtils.consume(responseEntity);
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        return gson.fromJson(responseString, StepicWrappers.TokenInfo.class);
      }
      else {
        LOG.warn("Failed to Login: " + statusLine.getStatusCode() + statusLine.getReasonPhrase());
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }
}
