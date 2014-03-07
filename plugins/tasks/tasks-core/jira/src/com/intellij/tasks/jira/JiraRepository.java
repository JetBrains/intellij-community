package com.intellij.tasks.jira;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import com.intellij.tasks.jira.rest.JiraRestApi;
import com.intellij.tasks.jira.soap.JiraSoapApi;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  public static final Gson GSON = TaskUtil.installDateDeserializer(new GsonBuilder()).create();
  private final static Logger LOG = Logger.getInstance(JiraRepository.class);
  public static final String REST_API_PATH = "/rest/api/latest";

  private static final boolean DEBUG_SOAP = Boolean.getBoolean("tasks.jira.soap");
  private static final boolean REDISCOVER_API = Boolean.getBoolean("tasks.jira.rediscover");

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
  }

  public JiraRepository(JiraRepositoryType type) {
    super(type);
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


  public JiraRepository clone() {
    return new JiraRepository(this);
  }

  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    ensureApiVersionDiscovered();
    String resultQuery = query;
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
    return ArrayUtil.toObjectArray(myApiVersion.findTasks(resultQuery, max), Task.class);
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
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
    if (DEBUG_SOAP) {
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
    if (myApiVersion == null || DEBUG_SOAP || REDISCOVER_API) {
      myApiVersion = discoverApiVersion();
    }
  }

  // Used primarily for XML_RPC API
  @NotNull
  public String executeMethod(@NotNull HttpMethod method) throws Exception {
    return executeMethod(getHttpClient(), method);
  }

  @NotNull
  public String executeMethod(@NotNull HttpClient client, @NotNull HttpMethod method) throws Exception {
    LOG.debug("URI: " + method.getURI());
    int statusCode;
    String entityContent;
    statusCode = client.executeMethod(method);
    LOG.debug("Status code: " + statusCode);
    // may be null if 204 No Content received
    final InputStream stream = method.getResponseBodyAsStream();
    entityContent = stream == null ? "" : StreamUtil.readText(stream, CharsetToolkit.UTF8);
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

  /**
   * Always use Basic HTTP authentication for JIRA REST interface
   */
  @Override
  public boolean isUseHttpAuthentication() {
    return true;
  }

  @Override
  protected void configureHttpClient(HttpClient client) {
    super.configureHttpClient(client);
    client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
  }

  @Override
  protected int getFeatures() {
    int features = super.getFeatures() | TIME_MANAGEMENT;
    if (myApiVersion == null || myApiVersion.getType() == JiraRemoteApi.ApiType.SOAP) {
      return features & ~NATIVE_SEARCH & ~STATE_UPDATING & ~TIME_MANAGEMENT;
    }
    return features;
  }

  public boolean isJqlSupported() {
    return myApiVersion != null && myApiVersion.getType() != JiraRemoteApi.ApiType.SOAP;
  }

  public String getSearchQuery() {
    return mySearchQuery;
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
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