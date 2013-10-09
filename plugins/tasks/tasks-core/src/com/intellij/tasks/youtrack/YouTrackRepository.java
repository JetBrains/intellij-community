package com.intellij.tasks.youtrack;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.utils.XMLChar;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@Tag("YouTrack")
public class YouTrackRepository extends BaseRepositoryImpl {

  private String myDefaultSearch = "for: me sort by: updated #Unresolved";
  private Map<TaskState, String> myCustomStateNames = new EnumMap<TaskState, String>(TaskState.class);

  // Default names for supported issues states
  {
    myCustomStateNames.put(TaskState.IN_PROGRESS, "In Progress");
    myCustomStateNames.put(TaskState.RESOLVED, "Fixed");
  }

  /**
   * for serialization
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public YouTrackRepository() {
  }

  public YouTrackRepository(TaskRepositoryType type) {
    super(type);
  }

  @Override
  public BaseRepository clone() {
    return new YouTrackRepository(this);
  }

  private YouTrackRepository(YouTrackRepository other) {
    super(other);
    myDefaultSearch = other.getDefaultSearch();
    myCustomStateNames = new EnumMap<TaskState, String>(other.getCustomStateNames());
  }

  public Task[] getIssues(@Nullable String request, int max, long since) throws Exception {

    String query = getDefaultSearch();
    if (request != null) {
      query += " " + request;
    }
    String requestUrl = "/rest/project/issues/?filter=" + encodeUrl(query) + "&max=" + max + "&updatedAfter" + since;
    HttpMethod method = doREST(requestUrl, false);
    InputStream stream = method.getResponseBodyAsStream();

    // todo workaround for http://youtrack.jetbrains.net/issue/JT-7984
    String s = StreamUtil.readText(stream, "UTF-8");
    for (int i = 0; i < s.length(); i++) {
      if (!XMLChar.isValid(s.charAt(i))) {
        s = s.replace(s.charAt(i), ' ');
      }
    }

    Element element;
    try {
      //InputSource source = new InputSource(stream);
      //source.setEncoding("UTF-8");
      //element = new SAXBuilder(false).build(source).getRootElement();
      element = new SAXBuilder(false).build(new StringReader(s)).getRootElement();
    }
    catch (JDOMException e) {
      LOG.error("Can't parse YouTrack response for " + requestUrl, e);
      throw e;
    }
    if ("error".equals(element.getName())) {
      throw new Exception("Error from YouTrack for " + requestUrl + ": '" + element.getText() + "'");
    }

    List<Element> children = element.getChildren("issue");

    final List<Task> tasks = ContainerUtil.mapNotNull(children, new NullableFunction<Element, Task>() {
      public Task fun(Element o) {
        return createIssue(o);
      }
    });
    return tasks.toArray(new Task[tasks.size()]);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    PostMethod method = new PostMethod(getUrl() + "/rest/user/login");
    return new HttpTestConnection<PostMethod>(method) {
      @Override
      protected void doTest(PostMethod method) throws Exception {
        login(method);
      }
    };
  }

  private HttpClient login(PostMethod method) throws Exception {
    if (method.getHostConfiguration().getProtocol() == null) {
      throw new Exception("Protocol not specified");
    }
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(getUsername(), getPassword()));
    configureHttpMethod(method);
    method.addParameter("login", getUsername());
    method.addParameter("password", getPassword());
    client.getParams().setContentCharset("UTF-8");
    client.executeMethod(method);
    if (method.getStatusCode() != 200) {
      throw new Exception("Cannot login: HTTP status code " + method.getStatusCode());
    }
    String response = method.getResponseBodyAsString(1000);
    if (response == null) {
      throw new NullPointerException();
    }
    if (!response.contains("<login>ok</login>")) {
      int pos = response.indexOf("</error>");
      int length = "<error>".length();
      if (pos > length) {
        response = response.substring(length, pos);
      }
      throw new Exception("Cannot login: " + response);
    }
    return client;
  }

  @Nullable
  public Task findTask(String id) throws Exception {
    HttpMethod method = doREST("/rest/issue/byid/" + id, false);
    InputStream stream = method.getResponseBodyAsStream();
    Element element = new SAXBuilder(false).build(stream).getRootElement();
    return element.getName().equals("issue") ? createIssue(element) : null;
  }


  HttpMethod doREST(String request, boolean post) throws Exception {
    HttpClient client = login(new PostMethod(getUrl() + "/rest/user/login"));
    String uri = getUrl() + request;
    HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    int status = client.executeMethod(method);
    if (status == 400) {
      InputStream string = method.getResponseBodyAsStream();
      Element element = new SAXBuilder(false).build(string).getRootElement();
      TaskUtil.prettyFormatXmlToLog(LOG, element);
      if ("error".equals(element.getName())) {
        throw new Exception(element.getText());
      }
    }
    return method;
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
    String s = myCustomStateNames.get(state);
    if (StringUtil.isEmpty(s)) {
      s = state.name();
    }
    doREST("/rest/issue/execute/" + task.getId() + "?command=" + encodeUrl("state " + s), true);
  }

  @Nullable
  private Task createIssue(Element element) {
    final String id = element.getAttributeValue("id");
    if (id == null) return null;
    final String summary = element.getAttributeValue("summary");
    if (summary == null) return null;
    final String description = element.getAttributeValue("description");

    String type = element.getAttributeValue("type");
    TaskType taskType = TaskType.OTHER;
    if (type != null) {
      try {
        taskType = TaskType.valueOf(type.toUpperCase());
      }
      catch (IllegalArgumentException e) {
        // do nothing
      }
    }
    final TaskType finalTaskType = taskType;

    final Date updated = new Date(Long.parseLong(element.getAttributeValue("updated")));
    final Date created = new Date(Long.parseLong(element.getAttributeValue("created")));

    return new Task() {
      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        return getUrl() + "/issue/" + getId();
      }

      @NotNull
      @Override
      public String getId() {
        return id;
      }

      @NotNull
      @Override
      public String getSummary() {
        return summary;
      }

      public String getDescription() {
        return description;
      }

      @NotNull
      @Override
      public Comment[] getComments() {
        return Comment.EMPTY_ARRAY;
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return LocalTaskImpl.getIconFromType(getType(), isIssue());
      }

      @NotNull
      @Override
      public TaskType getType() {
        return finalTaskType;
      }

      @Nullable
      @Override
      public Date getUpdated() {
        return updated;
      }

      @Nullable
      @Override
      public Date getCreated() {
        return created;
      }

      @Override
      public boolean isClosed() {
        return false;
      }

      @Override
      public TaskRepository getRepository() {
        return YouTrackRepository.this;
      }
    };
  }

  public String getDefaultSearch() {
    return myDefaultSearch;
  }

  public void setDefaultSearch(String defaultSearch) {
    if (defaultSearch != null) {
      myDefaultSearch = defaultSearch;
    }
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    YouTrackRepository repository = (YouTrackRepository)o;
    if (!Comparing.equal(repository.getDefaultSearch(), getDefaultSearch())) return false;
    if (!Comparing.equal(repository.getCustomStateNames(), getCustomStateNames())) return false;
    return true;
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.youtrack.YouTrackRepository");

  @Override
  public void updateTimeSpent(final LocalTask task, final String timeSpent, final String comment) throws Exception {
    checkVersion();
    final HttpMethod method = doREST("/rest/issue/execute/" + task.getId() + "?command=work+Today+" + timeSpent.replaceAll(" ", "+") + "+" + comment, true);
    if (method.getStatusCode() != 200) {
      InputStream stream = method.getResponseBodyAsStream();
      String message = new SAXBuilder(false).build(stream).getRootElement().getText();
      throw new Exception(message);
    }
  }

  private void checkVersion() throws Exception {
    HttpMethod method = doREST("/rest/workflow/version", false);
    InputStream stream = method.getResponseBodyAsStream();
    Element element = new SAXBuilder(false).build(stream).getRootElement();
    final boolean timeTrackingAvailable = element.getName().equals("version") && VersionComparatorUtil.compare(element.getChildText("version"), "4.1") >= 0;
    if (!timeTrackingAvailable) {
      throw new Exception("This version of Youtrack the time tracking is not supported");
    }
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | TIME_MANAGEMENT;
  }

  public void setCustomStateNames(Map<TaskState, String> customStateNames) {
    myCustomStateNames.putAll(customStateNames);
  }

  @Tag("customStates")
  @Property(surroundWithTag = false)
  @MapAnnotation(
    surroundWithTag = false,
    keyAttributeName = "state",
    valueAttributeName = "name",
    surroundKeyWithTag = false,
    surroundValueWithTag = false
  )

  public Map<TaskState, String> getCustomStateNames() {
    return myCustomStateNames;
  }

  public void setCustomStateName(TaskState state, String name) {
    myCustomStateNames.put(state, name);
  }
}
