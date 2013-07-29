package com.intellij.tasks.jira;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.jira.model.JiraIssue;
import com.intellij.tasks.jira.model.JiraResponseWrapper;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.jira.JiraRepository");
  public static final String LOGIN_FAILED_CHECK_YOUR_PERMISSIONS = "Login failed. Check your permissions.";
  public static final String REST_API_PATH_SUFFIX = "/rest/api/latest";

  private String mySearchQuery = "assignee = currentUser() order by duedate";

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

  public Task[] getIssues(@Nullable String searchQuery, int max, long since) throws Exception {
    HttpClient client = getHttpClient();
    GetMethod method = new GetMethod(getUrl() + REST_API_PATH_SUFFIX + "/search");
    String jqlQuery = mySearchQuery;
    if (!StringUtil.isEmpty(searchQuery)) {
      if (JiraUtil.ANY_ISSUE_KEY_REGEX.matcher(searchQuery).matches()) {
        jqlQuery += String.format(" and key = \"%s\"", searchQuery);
      }
      else {
        jqlQuery += String.format(" and summary ~ \"%s\"", searchQuery);
      }
    }
    method.setQueryString(new NameValuePair[]{
      new NameValuePair("jql", jqlQuery),
      // by default comment field will be skipped
      //new NameValuePair("fields", "*all"),
      new NameValuePair("fields", JiraIssue.REQUIRED_RESPONSE_FIELDS),
      new NameValuePair("maxResults", String.valueOf(max))
    });
    LOG.debug("URI is " + method.getURI());
    int statusCode = client.executeMethod(method);
    LOG.debug("Status code is " + statusCode);
    String entityContent = StreamUtil.readText(method.getResponseBodyAsStream(), "utf-8");
    LOG.debug(entityContent);
    if (statusCode != HttpStatus.SC_OK) {
      return Task.EMPTY_ARRAY;
    }
    JiraResponseWrapper.Issues wrapper = JiraUtil.GSON.fromJson(entityContent, JiraResponseWrapper.Issues.class);
    List<JiraIssue> issues = wrapper.getIssues();
    LOG.debug("Total " + issues.size() + " issues downloaded");
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
    HttpClient client = getHttpClient();
    GetMethod method = new GetMethod(getUrl() + REST_API_PATH_SUFFIX + "/issue/" + id);
    method.setQueryString("fields=" + encodeUrl(JiraIssue.REQUIRED_RESPONSE_FIELDS));
    int statusCode = client.executeMethod(method);
    LOG.debug("Status code is " + statusCode);
    String entityContent = StreamUtil.readText(method.getResponseBodyAsStream(), "utf-8");
    LOG.debug(entityContent);
    if (statusCode != HttpStatus.SC_OK) {
      return null;
    }
    return new JiraTask(JiraUtil.GSON.fromJson(entityContent, JiraIssue.class), this);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    String uri = getUrl() + REST_API_PATH_SUFFIX + "/search?maxResults=1";
    return new HttpTestConnection<GetMethod>(new GetMethod(uri)) {
      @Override
      public void doTest(GetMethod method) throws Exception {
        HttpClient client = getHttpClient();
        int statusCode = client.executeMethod(myMethod);
        if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
          throw new Exception(LOGIN_FAILED_CHECK_YOUR_PERMISSIONS);
        }
        else if (statusCode != HttpStatus.SC_OK) {
          throw new Exception("Error while connecting to server: " + HttpStatus.getStatusText(statusCode));
        }
      }
    };
  }

  public JiraRepository clone() {
    return new JiraRepository(this);
  }

  @Override
  protected int getFeatures() {
    return TIME_MANAGEMENT;
  }


  public String getSearchQuery() {
    return mySearchQuery;
  }

  public void setSearchQuery(String searchQuery) {
    mySearchQuery = searchQuery;
  }
}
