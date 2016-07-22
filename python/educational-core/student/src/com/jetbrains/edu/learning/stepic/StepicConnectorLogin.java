/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StepicConnectorLogin {
  private static final Logger LOG = Logger.getInstance(StepicConnectorLogin.class.getName());
  private static CloseableHttpClient ourClient;
  private static final String CLIENT_ID = "hUCWcq3hZHCmz0DKrDtwOWITLcYutzot7p4n59vU";
  private static String accessToken = "";

  // TODO sing_in
  public static CloseableHttpClient getHttpClient() {
    if (ourClient == null) {
      List<BasicHeader> headers = new ArrayList<>();
      if (!accessToken.isEmpty()) {
        headers.add(new BasicHeader("Authorization", "Bearer " + accessToken));
        headers.add(new BasicHeader("content-type", EduStepicNames.CONTENT_TYPE_APPL_JSON));
      }
      else {
        LOG.warn("access_token is empty");
      }
      ourClient = StepicConnectorInit.getBuilder().setDefaultHeaders(headers).build();
    }
    return ourClient;
  }

  //use it !!
  public static boolean login(@NotNull final Project project) {
    StepicUser user = StudyTaskManager.getInstance(project).getUser();
    String email = user.getEmail();
    if (StringUtil.isEmptyOrSpaces(email)) {
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      StepicUser defaultUser = StudyTaskManager.getInstance(defaultProject).getUser();
      String defaultEmail = defaultUser.getEmail();
      if (StringUtil.isEmptyOrSpaces(defaultEmail)) {
        throw new StepicAuthorizationException("Please authorize in the main menu.");
      }
      else {
        StudyTaskManager.getInstance(project).setUser(defaultUser);
      }
    }

    try {
      minorLogin(user);
    } catch (StepicAuthorizationException  e) {
      LOG.warn(e.getMessage());
      return false;
    }

    return true;
  }

  private static void minorLogin(StepicUser user){
    StepicWrappers.TokenInfo tokenInfo = postCredentials(user.getEmail(), user.getPassword());
    if (tokenInfo == null) {
      throw new StepicAuthorizationException("Wrong credentials");
    }
    accessToken = tokenInfo.accessToken;
    user.setupTokenInfo(tokenInfo);
    StepicUser tmpUser = StepicConnectorGet.getCurrentUser().users.get(0);
    user.update(tmpUser);
  }

  private static StepicWrappers.TokenInfo postCredentials(String user, String password) {
    final Gson gson = new GsonBuilder()
      .registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepicTaskFileAdapter())
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    final HttpPost request = new HttpPost(EduStepicNames.TOKEN_URL);
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("grant_type", "password"));
    nvps.add(new BasicNameValuePair("client_id", CLIENT_ID));
    nvps.add(new BasicNameValuePair("username", user));
    nvps.add(new BasicNameValuePair("password", password));

    request.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));

    try {
      final CloseableHttpResponse response = StepicConnectorInit.getHttpClient().execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        return gson.fromJson(responseString, StepicWrappers.TokenInfo.class);
      }
      else {
        LOG.warn("Failed to Login: " + statusLine.getStatusCode() + statusLine.getReasonPhrase());
        LOG.info("Failed to Login " + responseString);
        throw new IOException("Stepic returned non 200 status code " + responseString);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }

  public static void setAccessToken(String accessToken2) {
    accessToken = accessToken2;
  }

  public static String getAccessToken() {
    return accessToken;
  }
}
