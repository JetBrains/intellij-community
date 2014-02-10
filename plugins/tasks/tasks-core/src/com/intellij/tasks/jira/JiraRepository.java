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
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  public static final Gson GSON = TaskUtil.installDateDeserializer(new GsonBuilder()).create();
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.jira.JiraRepository");
  public static final String LOGIN_FAILED_CHECK_YOUR_PERMISSIONS = "Login failed. Check your permissions.";
  public static final String REST_API_PATH = "/rest/api/latest";

  /**
   * Default JQL query
   */
  private String mySearchQuery = "assignee = currentUser() and resolution = Unresolved order by updated";

  private JiraRestApi myRestApiVersion;

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
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (o.getClass() != getClass()) return false;
    return Comparing.equal(mySearchQuery, ((JiraRepository)o).mySearchQuery);
  }


  /**
   * Always use Basic HTTP authentication for JIRA REST interface
   */
  @Override
  public boolean isUseHttpAuthentication() {
    return true;
  }

  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    if (myRestApiVersion == null) {
      myRestApiVersion = discoverRestApiVersion();
    }
    String jqlQuery = mySearchQuery;
    if (!StringUtil.isEmpty(query)) {
      jqlQuery = String.format("summary ~ '%s'", query);
      if (!StringUtil.isEmpty(mySearchQuery)) {
        jqlQuery += String.format(" and %s", mySearchQuery);
      }
    }
    List<JiraIssue> issues = myRestApiVersion.findIssues(jqlQuery, max);
    return ContainerUtil.map2Array(issues, Task.class, new Function<JiraIssue, Task>() {
      @Override
      public JiraTask fun(JiraIssue issue) {
        return new JiraTask(issue, JiraRepository.this);
      }
    });
  }


  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    if (myRestApiVersion == null) {
      myRestApiVersion = discoverRestApiVersion();
    }
    JiraIssue issue = myRestApiVersion.findIssue(id);
    return issue == null ? null : new JiraTask(issue, this);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    String uri = getUrl() + REST_API_PATH + "/search?maxResults=1&jql=" + encodeUrl(mySearchQuery);
    return new HttpTestConnection<GetMethod>(new GetMethod(uri)) {
      @Override
      public void doTest(GetMethod method) throws Exception {
        executeMethod(method);
      }
    };
  }

  public JiraRepository clone() {
    return new JiraRepository(this);
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | TIME_MANAGEMENT;
  }

  public String getSearchQuery() {
    return mySearchQuery;
  }

  public void setSearchQuery(String searchQuery) {
    mySearchQuery = searchQuery;
  }

  @NotNull
  public JiraRestApi discoverRestApiVersion() throws Exception {
    String responseBody;
    try {
      responseBody = executeMethod(new GetMethod(getRestUrl("serverInfo")));
    }
    catch (Exception e) {
      LOG.warn("Can't find out JIRA REST API version");
      throw e;
    }
    JsonObject object = GSON.fromJson(responseBody, JsonObject.class);
    // when JIRA 4.x support will be dropped 'versionNumber' array in response
    // may be used instead version string parsing
    JiraRestApi version = JiraRestApi.fromJiraVersion(object.get("version").getAsString(), this);
    if (version == null) {
      throw new Exception("JIRA below 4.2.0 doesn't have REST API and is no longer supported.");
    }
    return version;
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
    myRestApiVersion.setTaskState(task, state);
  }

  @Override
  public void updateTimeSpent(@NotNull LocalTask task, @NotNull String timeSpent, @NotNull String comment) throws Exception {
    myRestApiVersion.updateTimeSpend(task, timeSpent, comment);
  }

  @NotNull
  public String executeMethod(@NotNull HttpMethod method) throws Exception {
    LOG.debug("URI: " + method.getURI());
    int statusCode;
    String entityContent;
    try {
      statusCode = getHttpClient().executeMethod(method);
      LOG.debug("Status code: " + statusCode);
      // may be null if 204 No Content received
      final InputStream stream = method.getResponseBodyAsStream();
      entityContent = stream == null ? "" : StreamUtil.readText(stream, CharsetToolkit.UTF8);
      TaskUtil.prettyFormatJsonToLog(LOG, entityContent);
    }
    finally {
      method.releaseConnection();
    }
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
          throw new Exception("Request failed. Reason: " + reason);
        }
      }
    }
    if (method.getResponseHeader("X-Authentication-Denied-Reason") != null) {
      Header header = method.getResponseHeader("X-Authentication-Denied-Reason");
      // only in JIRA >= 5.x.x
      if (header.getValue().startsWith("CAPTCHA_CHALLENGE")) {
        throw new Exception("Login failed. Enter captcha in web-interface.");
      }
    }
    if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      throw new Exception(LOGIN_FAILED_CHECK_YOUR_PERMISSIONS);
    }
    String statusText = HttpStatus.getStatusText(method.getStatusCode());
    throw new Exception(String.format("Request failed with HTTP error: %d %s", statusCode, statusText));
  }

  @Override
  public void setUrl(String url) {
    myRestApiVersion = null;
    super.setUrl(url);
  }

  public String getRestUrl(String... parts) {
    return getUrl() + REST_API_PATH + "/" + StringUtil.join(parts, "/");
  }
}