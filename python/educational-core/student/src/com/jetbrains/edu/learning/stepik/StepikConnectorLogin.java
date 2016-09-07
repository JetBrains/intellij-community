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
package com.jetbrains.edu.learning.stepik;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
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

public class StepikConnectorLogin {
  private static final Logger LOG = Logger.getInstance(StepikConnectorLogin.class.getName());
  private static CloseableHttpClient ourClient;
  private static final String CLIENT_ID = "hUCWcq3hZHCmz0DKrDtwOWITLcYutzot7p4n59vU";
  private static StepikUser currentUser;

  // TODO sing_in
  public static CloseableHttpClient getHttpClient() {
    if (ourClient == null) {
      List<BasicHeader> headers = new ArrayList<>();
      if (currentUser != null && currentUser.getAccessToken() != null && !currentUser.getAccessToken().isEmpty()) {
        headers.add(new BasicHeader("Authorization", "Bearer " + currentUser.getAccessToken()));
        headers.add(new BasicHeader("Content-type", EduStepikNames.CONTENT_TYPE_APPL_JSON));
      }
      else {
        LOG.error("access_token is empty");
//        showLoginDialog();
      }
      ourClient = StepikConnectorInit.getBuilder().setDefaultHeaders(headers).build();
    }
    return ourClient;
  }

  @Deprecated
  public static boolean login(@NotNull final Project project) {
    LOG.info("login");
    resetClient();
    StepikUser user = StudyTaskManager.getInstance(project).getUser();
    String email = user.getEmail();
    if (StringUtil.isEmptyOrSpaces(email)) {
      LOG.info("current project user is empty");
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      StepikUser defaultUser = StudyTaskManager.getInstance(defaultProject).getUser();
      String defaultEmail = defaultUser.getEmail();
      if (StringUtil.isEmptyOrSpaces(defaultEmail)) {
        throw new StepikAuthorizationException("Please authorize in the main menu.");
      }
      else {
        StudyTaskManager.getInstance(project).setUser(defaultUser);
      }
    }

    try {
      LOG.info("minor login");
      minorLogin(user);
    }
    catch (StepikAuthorizationException e) {
      LOG.warn(e.getMessage());
      return false;
    }

    return true;
  }

  public static boolean loginFromSettings(@NotNull final Project project, StepikUser basicUser) {
    resetClient();
    StepikUser user = minorLogin(basicUser);

    if (user == null) {
      return false;
    }
    else {
      StudyTaskManager.getInstance(project).setUser(user);

      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      if (defaultProject != project && (StudyTaskManager.getInstance(defaultProject)).getUser().getEmail().isEmpty()) {
        StudyTaskManager.getInstance(defaultProject).setUser(user);
      }
      return true;
    }
  }

  public static boolean loginFromDialog(@NotNull final Project project) {
    StepikUser user = StudyTaskManager.getInstance(project).getUser();
    StepikUser defaultUser = StudyTaskManager.getInstance(ProjectManager.getInstance().getDefaultProject()).getUser();
    final String email = user.getEmail();
    LOG.info("after get email");
    if (StringUtil.isEmptyOrSpaces(email)) {
      if (StringUtil.isEmptyOrSpaces(defaultUser.getEmail())) {
        return showLoginDialog();
      }
      else {
        defaultUser = minorLogin(defaultUser);
        StudyTaskManager.getInstance(project).setUser(defaultUser);
      }
    }
    else {
      if ((user = minorLogin(user)) == null) {
        return showLoginDialog();
      }
      if (user.getEmail().equals(defaultUser.getEmail()) || defaultUser.getEmail().isEmpty()){
        StudyTaskManager.getInstance(ProjectManager.getInstance().getDefaultProject()).setUser(user);
      }
      StudyTaskManager.getInstance(project).setUser(user);
    }
    return true;
  }

  public static boolean showLoginDialog() {
    final boolean[] logged = {false};
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final LoginDialog dialog = new LoginDialog();
      dialog.show();
      logged[0] = dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE;
    }, ModalityState.defaultModalityState());
    return logged[0];
  }

  public static void resetClient() {
    ourClient = null;
    currentUser = null;
  }

  public static StepikUser minorLogin(StepikUser basicUser) {
    String refreshToken;
    StepikWrappers.TokenInfo tokenInfo = null;
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();

    if ( !(refreshToken = basicUser.getRefreshToken()).isEmpty()){
      LOG.info("refresh_token auth");
      nvps.add(new BasicNameValuePair("client_id", CLIENT_ID));
      nvps.add(new BasicNameValuePair("content-type", "application/json"));
      nvps.add(new BasicNameValuePair("grant_type", "refresh_token"));
      nvps.add(new BasicNameValuePair("refresh_token", refreshToken));

      tokenInfo = postCredentials(nvps);
    }

    nvps.clear();

    if (tokenInfo == null){
      LOG.info("credentials auth");
      String password = basicUser.getPassword();
      if (password.isEmpty()) return null;
      nvps.add(new BasicNameValuePair("client_id", CLIENT_ID));
      nvps.add(new BasicNameValuePair("grant_type", "password"));
      nvps.add(new BasicNameValuePair("username", basicUser.getEmail()));
      nvps.add(new BasicNameValuePair("password", password));

      tokenInfo = postCredentials(nvps);
    }

    if (tokenInfo == null) {
      return null;
    }
    StepikUser user = new StepikUser(basicUser);
    user.setupTokenInfo(tokenInfo);
    currentUser = user;
    StepikUser userInfo = StepikConnectorGet.getCurrentUser().users.get(0);
    user.update(userInfo);
    return user;
  }

  private static StepikWrappers.TokenInfo postCredentials(List<NameValuePair> nvps) {
    final Gson gson = new GsonBuilder()
      .registerTypeAdapter(TaskFile.class, new StudySerializationUtils.Json.StepikTaskFileAdapter())
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .create();

    final HttpPost request = new HttpPost(EduStepikNames.TOKEN_URL);
    request.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));
    //for (NameValuePair pair : nvps){
    //  LOG.info(pair.getName() + " " + pair.getValue());
    //}

    try {
      final CloseableHttpResponse response = StepikConnectorInit.getHttpClient().execute(request);
      final StatusLine statusLine = response.getStatusLine();
      final HttpEntity responseEntity = response.getEntity();
      final String responseString = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        return gson.fromJson(responseString, StepikWrappers.TokenInfo.class);
      }
      else {
        LOG.warn("Failed to Login: " + statusLine.getStatusCode() + statusLine.getReasonPhrase());
        throw new IOException("Stepik returned non 200 status code " + responseString);
      }
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }
}