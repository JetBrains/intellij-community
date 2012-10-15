package com.intellij.tasks.youtrack;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.StringReader;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@Tag("YouTrack")
public class YouTrackRepository extends BaseRepositoryImpl {

  private String myDefaultSearch = "for: me sort by: updated #Unresolved";

  /** for serialization */
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
  }

  public List<Task> getIssues(@Nullable String request, int max, long since) throws Exception {

    String query = getDefaultSearch();
    if (request != null) {
      query += " " + request;
    }
    String requestUrl = "/rest/project/issues/?filter=" + encodeUrl(query) + "&max=" + max + "&updatedAfter" + since;
    HttpMethod method = doREST(requestUrl, false);
    InputStream stream = method.getResponseBodyAsStream();

    // workaround for http://youtrack.jetbrains.net/issue/JT-7984
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
    @SuppressWarnings({"unchecked"})
    List<Object> children = element.getChildren("issue");

    return ContainerUtil.mapNotNull(children, new Function<Object, Task>() {
      public Task fun(Object o) {
        return createIssue((Element)o);
      }
    });
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


  private HttpMethod doREST(String request, boolean post) throws Exception {
    HttpClient client = login(new PostMethod(getUrl() + "/rest/user/login"));
    String uri = getUrl() + request;
    HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    client.executeMethod(method);
    return method;
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
    String s;
    switch (state) {
      case IN_PROGRESS:
        s = "In+Progress";
        break;
      default:
        s = state.name();
    }
    doREST("/rest/issue/execute/" + task.getId() + "?command=state+" + s, true);
  }

  private Task createIssue(Element element) {
    String id = element.getAttributeValue("id");
    if (id == null) {
      return null;
    }
    String summary = element.getAttributeValue("summary");
    if (summary == null) {
      return null;
    }
    final String description = element.getAttributeValue("description");
    LocalTaskImpl task = new LocalTaskImpl(id, summary) {
      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        return getUrl() + "/issue/" + getId();
      }

      public String getDescription() {
        return description;
      }

      @Override
      public TaskRepository getRepository() {
        return YouTrackRepository.this;
      }
    };
    String type = element.getAttributeValue("type");

    if (type != null) {
      try {
        task.setType(TaskType.valueOf(type.toUpperCase()));
      }
      catch (IllegalArgumentException e) {
        // do nothing
      }
    }

    task.setUpdated(new Date(Long.parseLong(element.getAttributeValue("updated"))));
    task.setCreated(new Date(Long.parseLong(element.getAttributeValue("created"))));
    return task;
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
    return super.equals(o) && Comparing.equal(((YouTrackRepository)o).getDefaultSearch(), getDefaultSearch());
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.youtrack.YouTrackRepository");
}
