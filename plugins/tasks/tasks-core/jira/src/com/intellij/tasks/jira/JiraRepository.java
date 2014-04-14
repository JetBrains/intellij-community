package com.intellij.tasks.jira;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.impl.gson.GsonUtil;
import com.intellij.tasks.jira.rest.JiraRestApi;
import com.intellij.tasks.jira.soap.JiraSoapApi;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  public static final Gson GSON = GsonUtil.createDefaultBuilder().create();
  private final static Logger LOG = Logger.getInstance(JiraRepository.class);
  public static final String REST_API_PATH = "/rest/api/latest";

  private static final boolean LEGACY_API_ONLY = Boolean.getBoolean("tasks.jira.legacy.api.only");
  private static final boolean REDISCOVER_API = Boolean.getBoolean("tasks.jira.rediscover.api");

  public static final Pattern JIRA_ID_PATTERN = Pattern.compile("\\p{javaUpperCase}+-\\d+");

  /**
   * Default JQL query
   */
  private String mySearchQuery = TaskBundle.message("jira.default.query");

  private JiraRemoteApi myApiVersion;

  /**
   * Serialization constructor
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public JiraRepository() {
    myUseHttpAuthentication = true;
  }

  public JiraRepository(JiraRepositoryType type) {
    super(type);
    // Use Basic authentication at the beginning of new session and disable then if needed
    myUseHttpAuthentication = true;
  }

  private JiraRepository(JiraRepository other) {
    super(other);
    mySearchQuery = other.mySearchQuery;
    if (other.myApiVersion != null) {
      myApiVersion = other.myApiVersion.getType().createApi(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (o.getClass() != getClass()) return false;
    return Comparing.equal(mySearchQuery, ((JiraRepository)o).mySearchQuery);
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
        tasksFound = ContainerUtil.concat(true, tasksFound, task);
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
    // TODO cancellable connection for XML_RPC?
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        ensureApiVersionDiscovered();
        myApiVersion.findTasks("", 1);
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
      return new JiraSoapApi(this);
    }

    String responseBody;
    GetMethod method = new GetMethod(getRestUrl("serverInfo"));
    try {
      responseBody = executeMethod(method);
    }
    catch (Exception e) {
      // probably JIRA version prior 4.2
      // without hasBeenUsed() check getStatusCode() might throw NPE, if connection was refused
      if (method.hasBeenUsed() && method.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return new JiraSoapApi(this);
      }
      else {
        throw e;
      }
    }
    JsonObject object = GSON.fromJson(responseBody, JsonObject.class);
    // when JIRA 4.x support will be dropped 'versionNumber' array in response
    // may be used instead version string parsing
    JiraRestApi restApi = JiraRestApi.fromJiraVersion(object.get("version").getAsString(), this);
    if (restApi == null) {
      throw new Exception(TaskBundle.message("jira.failure.no.REST"));
    }
    return restApi;
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
    boolean cookieAuthenticated = false;
    for (Cookie cookie : client.getState().getCookies()) {
      if (cookie.getName().equals("JSESSIONID") && !cookie.isExpired()) {
        cookieAuthenticated = true;
        break;
      }
    }
    // disable subsequent basic authorization attempts if user already was authenticated
    boolean enableBasicAuthentication = !(isRestApiSupported() && cookieAuthenticated);
    if (enableBasicAuthentication != isUseHttpAuthentication()) {
      LOG.info("Basic authentication for subsequent requests was " + (enableBasicAuthentication ? "enabled" : "disabled"));
    }
    setUseHttpAuthentication(enableBasicAuthentication);

    int statusCode = client.executeMethod(method);
    LOG.debug("Status code: " + statusCode);
    // may be null if 204 No Content received
    final InputStream stream = method.getResponseBodyAsStream();
    String entityContent = stream == null ? "" : StreamUtil.readText(stream, CharsetToolkit.UTF8);
    TaskUtil.prettyFormatJsonToLog(LOG, entityContent);
    // besides SC_OK, can also be SC_NO_CONTENT in issue transition requests
    // see: JiraRestApi#setTaskStatus
    //if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NO_CONTENT) {
    if (statusCode >= 200 && statusCode < 300) {
      return entityContent;
    }
    else if (method.getResponseHeader("Content-Type") != null) {
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
    return myApiVersion != null && myApiVersion.getType() != JiraRemoteApi.ApiType.SOAP;
  }

  public boolean isJqlSupported() {
    return isRestApiSupported();
  }

  public String getSearchQuery() {
    return mySearchQuery;
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception {
    myApiVersion.setTaskState(task, state);
  }

  public void setSearchQuery(String searchQuery) {
    mySearchQuery = searchQuery;
  }

  @Override
  public void setUrl(String url) {
    // reset remote API version, only if server URL was changed
    if (!getUrl().equals(url)) {
      myApiVersion = null;
      super.setUrl(url);
    }
  }

  /**
   * Used to preserve discovered API version for the next initialization.
   *
   * @return
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

  public String getRestUrl(String... parts) {
    return getUrl() + REST_API_PATH + "/" + StringUtil.join(parts, "/");
  }
}