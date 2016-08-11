/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.tasks.jira;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.jira.rest.JiraRestApi;
import com.intellij.tasks.jira.soap.JiraLegacyApi;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.xmlrpc.CommonsXmlRpcTransport;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  public static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private final static Logger LOG = Logger.getInstance(JiraRepository.class);
  public static final String REST_API_PATH = "/rest/api/latest";

  private static final boolean LEGACY_API_ONLY = Boolean.getBoolean("tasks.jira.legacy.api.only");
  private static final boolean BASIC_AUTH_ONLY = Boolean.getBoolean("tasks.jira.basic.auth.only");
  private static final boolean REDISCOVER_API = Boolean.getBoolean("tasks.jira.rediscover.api");

  public static final Pattern JIRA_ID_PATTERN = Pattern.compile("\\p{javaUpperCase}+-\\d+");
  public static final String AUTH_COOKIE_NAME = "JSESSIONID";

  /**
   * Default JQL query
   */
  private String mySearchQuery = TaskBundle.message("jira.default.query");

  private JiraRemoteApi myApiVersion;
  private String myJiraVersion;
  private boolean myInCloud = false;

  /**
   * Serialization constructor
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public JiraRepository() {
    setUseHttpAuthentication(true);
  }

  public JiraRepository(JiraRepositoryType type) {
    super(type);
    // Use Basic authentication at the beginning of new session and disable then if needed
    setUseHttpAuthentication(true);
  }

  private JiraRepository(JiraRepository other) {
    super(other);
    mySearchQuery = other.mySearchQuery;
    myJiraVersion = other.myJiraVersion;
    myInCloud = other.myInCloud;
    if (other.myApiVersion != null) {
      myApiVersion = other.myApiVersion.getType().createApi(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof JiraRepository)) return false;

    JiraRepository repository = (JiraRepository)o;

    if (!Comparing.equal(mySearchQuery, repository.getSearchQuery())) return false;
    if (!Comparing.equal(myJiraVersion, repository.getJiraVersion())) return false;
    if (!Comparing.equal(myInCloud, repository.isInCloud())) return false;
    return true;
  }


  @NotNull
  public JiraRepository clone() {
    return new JiraRepository(this);
  }

  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    ensureApiVersionDiscovered();
    String resultQuery = StringUtil.notNullize(query);
    if (isJqlSupported()) {
      if (StringUtil.isNotEmpty(mySearchQuery) && StringUtil.isNotEmpty(query)) {
        resultQuery = String.format("summary ~ '%s' and ", query) + mySearchQuery;
      }
      else if (StringUtil.isNotEmpty(query)) {
        resultQuery = String.format("summary ~ '%s'", query);
      }
      else {
        resultQuery = mySearchQuery;
      }
    }
    List<Task> tasksFound = myApiVersion.findTasks(resultQuery, max);
    // JQL matching doesn't allow to do something like "summary ~ query or key = query"
    // and it will return error immediately. So we have to search in two steps to provide
    // behavior consistent with e.g. YouTrack.
    // looks like issue ID
    if (query != null && JIRA_ID_PATTERN.matcher(query.trim()).matches()) {
      Task task = findTask(query);
      if (task != null) {
        tasksFound = ContainerUtil.append(tasksFound, task);
      }
    }
    return ArrayUtil.toObjectArray(tasksFound, Task.class);
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    ensureApiVersionDiscovered();
    return myApiVersion.findTask(id);
  }

  @Override
  public void updateTimeSpent(@NotNull LocalTask task, @NotNull String timeSpent, @NotNull String comment) throws Exception {
    myApiVersion.updateTimeSpend(task, timeSpent, comment);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    clearCookies();
    // TODO cancellable connection for XML_RPC?
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        ensureApiVersionDiscovered();
        myApiVersion.findTasks(mySearchQuery, 1);
      }

      @Override
      public void cancel() {
        // do nothing for now
      }
    };
  }

  @NotNull
  public JiraRemoteApi discoverApiVersion() throws Exception {
    if (LEGACY_API_ONLY) {
      LOG.info("Intentionally using only legacy JIRA API");
      return createLegacyApi();
    }

    String responseBody;
    GetMethod method = new GetMethod(getRestUrl("serverInfo"));
    try {
      responseBody = executeMethod(method);
    }
    catch (Exception e) {
      // probably JIRA version prior 4.2
      // It's not safe to call HttpMethod.getStatusCode() directly, because it will throw NPE
      // if response was not received (connection lost etc.) and hasBeenUsed()/isRequestSent() are
      // not the way to check it safely.
      StatusLine status = method.getStatusLine();
      if (status != null && status.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return createLegacyApi();
      }
      else {
        throw e;
      }
    }
    JsonObject serverInfo = GSON.fromJson(responseBody, JsonObject.class);
    // when JIRA 4.x support will be dropped 'versionNumber' array in response
    // may be used instead version string parsing
    myJiraVersion = serverInfo.get("version").getAsString();
    final boolean hostedInCloud = hostEndsWith(serverInfo.get("baseUrl").getAsString(), "atlassian.net");
    // Legacy JIRA onDemand versions contained "OD" abbreviation
    myInCloud = StringUtil.notNullize(myJiraVersion).contains("OD") || hostedInCloud;
    LOG.info("JIRA version (from serverInfo): " + myJiraVersion + (myInCloud ? " (Cloud)" : ""));
    if (isInCloud()) {
      LOG.info("Connecting to JIRA on-Demand. Cookie authentication is enabled unless 'tasks.jira.basic.auth.only' VM flag is used.");
    }
    JiraRestApi restApi = JiraRestApi.fromJiraVersion(myJiraVersion, this);
    if (restApi == null) {
      throw new Exception(TaskBundle.message("jira.failure.no.REST"));
    }
    return restApi;
  }

  private static boolean hostEndsWith(@NotNull String url, @NotNull String suffix) {
    try {
      final URL parsed = new URL(url);
      return parsed.getHost().endsWith(suffix);
    }
    catch (MalformedURLException ignored) {
    }
    return false;
  }

  private JiraLegacyApi createLegacyApi() {
    try {
      XmlRpcClient client = new XmlRpcClient(getUrl());
      Vector<String> parameters = new Vector<>(Collections.singletonList(""));
      XmlRpcRequest request = new XmlRpcRequest("jira1.getServerInfo", parameters);
      @SuppressWarnings("unchecked") Hashtable<String, Object> response =
        (Hashtable<String, Object>)client.execute(request, new CommonsXmlRpcTransport(new URL(getUrl()), getHttpClient()));
      if (response != null) {
        myJiraVersion = (String)response.get("version");
      }
    }
    catch (Exception e) {
      LOG.error("Cannot find out JIRA version via XML-RPC", e);
    }
    return new JiraLegacyApi(this);
  }

  private void ensureApiVersionDiscovered() throws Exception {
    if (myApiVersion == null || LEGACY_API_ONLY || REDISCOVER_API) {
      myApiVersion = discoverApiVersion();
    }
  }

  @NotNull
  public String executeMethod(@NotNull HttpMethod method) throws Exception {
    LOG.debug("URI: " + method.getURI());

    HttpClient client = getHttpClient();
    // Fix for https://jetbrains.zendesk.com/agent/#/tickets/24566
    // See https://confluence.atlassian.com/display/ONDEMANDKB/Getting+randomly+logged+out+of+OnDemand for details
    // IDEA-128824, IDEA-128706 Use cookie authentication only for JIRA on-Demand
    // TODO Make JiraVersion more suitable for such checks
    if (BASIC_AUTH_ONLY || !isInCloud()) {
      // to override persisted settings
      setUseHttpAuthentication(true);
    }
    else {
      boolean enableBasicAuthentication = !(isRestApiSupported() && containsCookie(client, AUTH_COOKIE_NAME));
      if (enableBasicAuthentication != isUseHttpAuthentication()) {
        LOG.info("Basic authentication for subsequent requests was " + (enableBasicAuthentication ? "enabled" : "disabled"));
      }
      setUseHttpAuthentication(enableBasicAuthentication);
    }

    int statusCode = client.executeMethod(method);
    LOG.debug("Status code: " + statusCode);
    // may be null if 204 No Content received
    final InputStream stream = method.getResponseBodyAsStream();
    String entityContent = stream == null ? "" : StreamUtil.readText(stream, CharsetToolkit.UTF8);
    //TaskUtil.prettyFormatJsonToLog(LOG, entityContent);
    // besides SC_OK, can also be SC_NO_CONTENT in issue transition requests
    // see: JiraRestApi#setTaskStatus
    //if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
    if (statusCode >= 200 && statusCode < 300) {
      return entityContent;
    }
    clearCookies();
    if (method.getResponseHeader("Content-Type") != null) {
      Header header = method.getResponseHeader("Content-Type");
      if (header.getValue().startsWith("application/json")) {
        JsonObject object = GSON.fromJson(entityContent, JsonObject.class);
        if (object.has("errorMessages")) {
          String reason = StringUtil.join(object.getAsJsonArray("errorMessages"), " ");
          // something meaningful to user, e.g. invalid field name in JQL query
          LOG.warn(reason);
          throw new Exception(TaskBundle.message("failure.server.message", reason));
        }
      }
    }
    if (method.getResponseHeader("X-Authentication-Denied-Reason") != null) {
      Header header = method.getResponseHeader("X-Authentication-Denied-Reason");
      // only in JIRA >= 5.x.x
      if (header.getValue().startsWith("CAPTCHA_CHALLENGE")) {
        throw new Exception(TaskBundle.message("jira.failure.captcha"));
      }
    }
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      throw new Exception(TaskBundle.message("failure.login"));
    }
    String statusText = HttpStatus.getStatusText(method.getStatusCode());
    throw new Exception(TaskBundle.message("failure.http.error", statusCode, statusText));
  }

  public boolean isInCloud() {
    return myInCloud;
  }

  public void setInCloud(boolean inCloud) {
    myInCloud = inCloud;
  }

  private static boolean containsCookie(@NotNull HttpClient client, @NotNull String cookieName) {
    for (Cookie cookie : client.getState().getCookies()) {
      if (cookie.getName().equals(cookieName) && !cookie.isExpired()) {
        return true;
      }
    }
    return false;
  }

  private void clearCookies() {
    getHttpClient().getState().clearCookies();
  }

  // Made public for SOAP API compatibility
  @Override
  public HttpClient getHttpClient() {
    return super.getHttpClient();
  }

  @Override
  protected void configureHttpClient(HttpClient client) {
    super.configureHttpClient(client);
    client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
  }

  @Override
  protected int getFeatures() {
    int features = super.getFeatures();
    if (isRestApiSupported()) {
      return features | TIME_MANAGEMENT | STATE_UPDATING;
    }
    else {
      return features & ~NATIVE_SEARCH & ~STATE_UPDATING & ~TIME_MANAGEMENT;
    }
  }

  private boolean isRestApiSupported() {
    return myApiVersion != null && myApiVersion.getType() != JiraRemoteApi.ApiType.LEGACY;
  }

  public boolean isJqlSupported() {
    return isRestApiSupported();
  }

  public String getSearchQuery() {
    return mySearchQuery;
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    myApiVersion.setTaskState(task, state);
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    return myApiVersion.getAvailableTaskStates(task);
  }

  public void setSearchQuery(String searchQuery) {
    mySearchQuery = searchQuery;
  }

  @Override
  public void setUrl(String url) {
    // Compare only normalized URLs
    final String oldUrl = getUrl();
    super.setUrl(url);
    // reset remote API version, only if server URL was changed
    if (!getUrl().equals(oldUrl)) {
      myApiVersion = null;
      myInCloud = false;
    }
  }

  /**
   * Used to preserve discovered API version for the next initialization.
   */
  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public JiraRemoteApi.ApiType getApiType() {
    return myApiVersion == null ? null : myApiVersion.getType();
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setApiType(@Nullable JiraRemoteApi.ApiType type) {
    if (type != null) {
      myApiVersion = type.createApi(this);
    }
  }

  @Nullable
  public String getJiraVersion() {
    return myJiraVersion;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setJiraVersion(@Nullable String jiraVersion) {
    myJiraVersion = jiraVersion;
  }

  public String getRestUrl(String... parts) {
    return getUrl() + REST_API_PATH + "/" + StringUtil.join(parts, "/");
  }
}