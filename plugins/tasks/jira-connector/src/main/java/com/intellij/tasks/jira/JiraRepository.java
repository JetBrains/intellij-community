package com.intellij.tasks.jira;

import com.atlassian.theplugin.jira.api.JIRAIssueBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl {

  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.jira.JiraRepository");

  private boolean myJira4 = true;

  /**
   * for serialization
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public JiraRepository() {
    super();
  }

  private JiraRepository(JiraRepository other) {
    super(other);
  }

  public JiraRepository(JiraRepositoryType type) {
    super(type);
  }

  public Task[] getIssues(String request, int max, long since) throws Exception {
    return getIssues(max, login());
  }

  private Task[] getIssues(int max, HttpClient httpClient) throws IOException, JDOMException {
    StringBuilder url = new StringBuilder(getUrl());
    url.append("/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?");
    url.append("tempMax=").append(max);
    url.append("&assignee=").append(encodeUrl(getUsername()));
//      url.append("&resolution=-1");
    url.append("&reset=true");
    url.append("&sorter/field=").append("updated");
    url.append("&sorter/order=").append("DESC");
    url.append("&pager/start=").append(0);

    return processRSS(url.toString(), httpClient);
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
  }

  private Task[] processRSS(String url, HttpClient client) throws IOException, JDOMException {
    GetMethod method = new GetMethod(url);
    configureHttpMethod(method);
    client.executeMethod(method);

    int code = method.getStatusCode();
    if (code != HttpStatus.SC_OK) {
      throw new IOException(code == HttpStatus.SC_BAD_REQUEST ?
                            method.getResponseBodyAsString() :
                            ("HTTP " + code + " (" + HttpStatus.getStatusText(code) + ") " + method.getStatusText()));
    }
    InputStream stream = method.getResponseBodyAsStream();
    Element root = new SAXBuilder(false).build(stream).getRootElement();
    Element channel = root.getChild("channel");
    if (channel != null) {
      @SuppressWarnings({"unchecked"}) List<Element> children = channel.getChildren("item");
      LOG.info("JIRA: " + children.size() + " issues found");
      return ContainerUtil.map2Array(children, Task.class, new Function<Element, Task>() {
        public Task fun(Element o) {
          return new JiraTask(new JIRAIssueBean(getUrl(), o, false)) {
            @Override
            public TaskRepository getRepository() {
              return JiraRepository.this;
            }
          };
        }
      });
    }
    else {
      LOG.warn("JIRA channel not found");
    }
    return Task.EMPTY_ARRAY;
  }

  private HttpClient login() throws IOException {
    PostMethod postMethod = getLoginMethod();
    HttpClient client = getHttpClient();
    client.executeMethod(postMethod);
    if (!checkLoginResult(postMethod)) {
      // try 3.x protocol
      return login();
    }
    return client;
  }

  private boolean checkLoginResult(PostMethod postMethod) throws IOException {
    int statusCode = postMethod.getStatusCode();
    if (statusCode == HttpStatus.SC_NOT_FOUND && myJira4) {
      myJira4 = false;
      return false;
    }
    if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_MOVED_TEMPORARILY) {
      throw new IOException("Can't login: " + statusCode + " (" + HttpStatus.getStatusText(statusCode) + ")");
    }
    return true;
  }

  private PostMethod getLoginMethod() {
    String url = getUrl() + (myJira4 ? "/rest/gadget/1.0/login" : "/secure/Dashboard.jspa");
    PostMethod postMethod = new PostMethod(url);
    postMethod.addParameter("os_username", getUsername());
    postMethod.addParameter("os_password", getPassword());
    postMethod.addParameter("os_destination", "/success");
    configureHttpMethod(postMethod);
    return postMethod;
  }

  @Override
  public CancellableConnection createCancellableConnection() {
    PostMethod method = getLoginMethod();
    return new HttpTestConnection<PostMethod>(method) {

      @Override
      public void doTest(PostMethod method) throws Exception {
        HttpClient client = getHttpClient();
        client.executeMethod(myMethod);
        if (!checkLoginResult(method)) {
          myMethod = getLoginMethod(); // another try
          client.executeMethod(myMethod);
        }
        if (checkLoginResult(myMethod)) {
          getIssues(1, client);
        }
      }
    };
  }

  public JiraRepository clone() {
    return new JiraRepository(this);
  }

  @Nullable
  public Task findTask(String id) {
    try {
      StringBuilder url = new StringBuilder(getUrl());
      url.append("/si/jira.issueviews:issue-xml/");
      url.append(id).append('/').append(id).append(".xml");

      Task[] tasks = processRSS(url.toString(), login());
      return tasks.length == 0 ? null : tasks[0];
    }
    catch (Exception e) {
      LOG.warn("Cannot get issue " + id + ": " + e.getMessage());
      return null;
    }
  }
}
