package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.edu.learning.StudySettings;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.edu.learning.stepic.EduStepicClient.getBuilder;

public class EduStepicAuthorizedClient {
  private static final Logger LOG = Logger.getInstance(EduStepicAuthorizedClient.class.getName());

  private static CloseableHttpClient ourClient;

  private EduStepicAuthorizedClient() {
  }

  @Nullable
  public static CloseableHttpClient getHttpClient() {
    if (ourClient != null) {
      return ourClient;
    }

    StudySettings studySettings = StudySettings.getInstance();

    assert studySettings.getUser() != null: "User must not be null";

    StepicUser stepicUser = studySettings.getUser();
    assert stepicUser != null;

    if (!EduStepicClient.isTokenUpToDate(stepicUser.getAccessToken())) {
      StepicWrappers.TokenInfo tokens = getUpdatedTokens(stepicUser.getRefreshToken());
      if (tokens != null) {
        stepicUser.setTokenInfo(tokens);
      }
      else {
        return null;
      }
    }

    ourClient = createInitializedClient(stepicUser.getAccessToken());

    return ourClient;
  }

  @Nullable
  public static <T> T getFromStepic(@NotNull String link, @NotNull final Class<T> container) throws IOException {
    final CloseableHttpClient client = getHttpClient();
    return client == null ? null : EduStepicClient.getFromStepic(link, container, client);
  }

  /*
   * This method should be used only in project generation while project is not available.
   * Make sure you saved stepic user in task manager after using this method.
   */
  @NotNull
  public static CloseableHttpClient getHttpClient(@NotNull final StepicUser stepicUser) {
    if (ourClient != null) {
      return ourClient;
    }

    if (!EduStepicClient.isTokenUpToDate(stepicUser.getAccessToken())) {
      StepicWrappers.TokenInfo tokenInfo = getUpdatedTokens(stepicUser.getRefreshToken());
      if (tokenInfo != null) {
        stepicUser.setTokenInfo(tokenInfo);
      }
      else {
        return EduStepicClient.getHttpClient();
      }
    }

    ourClient = createInitializedClient(stepicUser.getAccessToken());

    return ourClient;
  }

   /*
   * This method should be used only in project generation while project is not available.
   * Make sure you saved stepic user in task manager after using this method.
   */
  public static <T> T getFromStepic(String link, final Class<T> container, @NotNull final StepicUser stepicUser) throws IOException {
    return EduStepicClient.getFromStepic(link, container, getHttpClient(stepicUser));
  }

  @NotNull
  private static CloseableHttpClient createInitializedClient(@NotNull String accessToken) {
    final List<BasicHeader> headers = new ArrayList<>();
    headers.add(new BasicHeader("Authorization", "Bearer " + accessToken));
    headers.add(new BasicHeader("Content-type", EduStepicNames.CONTENT_TYPE_APP_JSON));
    return getBuilder().setDefaultHeaders(headers).build();
  }

  @Nullable
  public static StepicUser login(@NotNull final String code, String redirectUrl) {
    final List<NameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("grant_type", "authorization_code"));
    parameters.add(new BasicNameValuePair("code", code));
    parameters.add(new BasicNameValuePair("redirect_uri", redirectUrl));
    parameters.add(new BasicNameValuePair("client_id", EduStepicNames.CLIENT_ID));

    StepicWrappers.TokenInfo tokenInfo = getTokens(parameters);
    if (tokenInfo != null) {
      final StepicUser user = new StepicUser(tokenInfo);
      ourClient = createInitializedClient(user.getAccessToken());

      final StepicUser currentUser = getCurrentUser();
      if (currentUser != null) {
        user.setId(currentUser.getId());
        user.setFirstName(currentUser.getFirstName());
        user.setLastName(currentUser.getLastName());
      }
      return user;
    }

    return null;
  }

  public static void invalidateClient() {
    ourClient = null;
  }

  @Nullable
  private static StepicWrappers.TokenInfo getUpdatedTokens(@NotNull final String refreshToken) {
    final List<NameValuePair> parameters = new ArrayList<>();
    parameters.add(new BasicNameValuePair("client_id", EduStepicNames.CLIENT_ID));
    parameters.add(new BasicNameValuePair("content-type", "application/json"));
    parameters.add(new BasicNameValuePair("grant_type", "refresh_token"));
    parameters.add(new BasicNameValuePair("refresh_token", refreshToken));

    return getTokens(parameters);
  }

  @Nullable
  public static StepicUser getCurrentUser() {
    CloseableHttpClient client = getHttpClient();
    if (client != null) {
      try {
        final StepicWrappers.AuthorWrapper wrapper = EduStepicClient.getFromStepic(EduStepicNames.CURRENT_USER,
                                                                                   StepicWrappers.AuthorWrapper.class,
                                                                                   client);
        if (wrapper != null && !wrapper.users.isEmpty()) {
          return wrapper.users.get(0);
        }
      }
      catch (IOException e) {
        LOG.warn("Couldn't get a current user");
      }
    }
    return null;
  }

  @Nullable
  private static StepicWrappers.TokenInfo getTokens(@NotNull final List<NameValuePair> parameters) {
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
        LOG.warn("Failed to get tokens: " + statusLine.getStatusCode() + statusLine.getReasonPhrase());
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return null;
  }
}
