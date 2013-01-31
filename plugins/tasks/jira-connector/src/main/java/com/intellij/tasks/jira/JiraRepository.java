package com.intellij.tasks.jira;

import com.atlassian.connector.commons.jira.soap.axis.JiraSoapService;
import com.atlassian.connector.commons.jira.soap.axis.JiraSoapServiceServiceLocator;
import com.atlassian.theplugin.jira.api.JIRAIssueBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.AxisProperties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;
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

  public Task[] getIssues(@Nullable String request, int max, long since) throws Exception {
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
      final List<Task> tasks = ContainerUtil.map(children, new Function<Element, Task>() {
        public Task fun(Element o) {
          return new JiraTask(new JIRAIssueBean(getUrl(), o, false)) {
            @Override
            public TaskRepository getRepository() {
              return JiraRepository.this;
            }
          };
        }
      });
      return tasks.toArray(new Task[tasks.size()]);
    }
    else {
      LOG.warn("JIRA channel not found");
    }
    return Task.EMPTY_ARRAY;
  }

  private HttpClient login() throws Exception {

    HttpClient client = getHttpClient();
    if (myJira4) {
      PostMethod postMethod = getLoginMethodFor4x();
      client.executeMethod(postMethod);
      if (checkLoginResult(postMethod)) {
        return client;
      }
    }
    // try 3.x protocol
    axisLogin();
    return client;
  }

  private void axisLogin() throws Exception {

    try {
      JiraSoapService soapService =
        new JiraSoapServiceServiceLocator().getJirasoapserviceV2(new URL(getUrl() + "/rpc/soap/jirasoapservice-v2"));
      if (isUseProxy()) {
        final List<KeyValue<String,String>> list = HttpConfigurable.getJvmPropertiesList(false, null);
        if (! list.isEmpty()) {
          for (KeyValue<String, String> value : list) {
            AxisProperties.setProperty(value.getKey(), value.getValue());
          }
        }
      }

      soapService.login(getUsername(), getPassword());
    }
    catch (RemoteException e) {
      String message = e.toString();
      int i = message.indexOf(": ");
      if (i > 0) {
        message = message.substring(i + 2);
      }
      throw new Exception(message, e);
    }
  }

  private boolean checkLoginResult(PostMethod postMethod) throws IOException {
    int statusCode = postMethod.getStatusCode();
    if (statusCode == HttpStatus.SC_NOT_FOUND) {
      myJira4 = false;
      return false;
    }
    if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_MOVED_TEMPORARILY) {
      throw new IOException("Can't login: " + statusCode + " (" + HttpStatus.getStatusText(statusCode) + ")");
    }
    return true;
  }

  private PostMethod getLoginMethodFor4x() {
    String url = getUrl() + "/rest/gadget/1.0/login";
    PostMethod postMethod = new PostMethod(url);
    postMethod.addParameter("os_username", getUsername());
    postMethod.addParameter("os_password", getPassword());
    postMethod.addParameter("os_destination", "/success");
    configureHttpMethod(postMethod);
    return postMethod;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    PostMethod method = getLoginMethodFor4x();
    return new HttpTestConnection<PostMethod>(method) {

      @Override
      public void doTest(PostMethod method) throws Exception {
        HttpClient client = getHttpClient();
        client.executeMethod(myMethod);
        if (!checkLoginResult(method)) {
          axisLogin();
        }
        getIssues(1, client);
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

  @Override
  public void updateTimeSpent(final LocalTask task, final String timeSpent, final String comment) throws Exception {
    final HttpClient client = login();
    checkVersion(client);
    PostMethod method = new PostMethod(getUrl() + "/rest/api/2/issue/" + task.getId() + "/worklog");
    method.setRequestEntity(
      new StringRequestEntity("{\"timeSpent\" : \"" + timeSpent + "\"" +
                              (StringUtil.isNotEmpty(comment) ? ", \"comment\" : " + comment : "")
                              + " }", "application/json", "UTF-8"));
    client.executeMethod(method);
    if (method.getStatusCode() != 201) {
      throw new Exception(method.getResponseBodyAsString());
    }
  }

  private void checkVersion(final HttpClient client) throws Exception {
    GetMethod method = new GetMethod(getUrl() + "/rest/api/2/project");
    client.executeMethod(method);
    if (method.getStatusCode() != 200) {
      throw new Exception("This version of JIRA doesn't have support REST API for working with worklog items.");
    }
  }

  @Override
  protected int getFeatures() {
    return BASIC_HTTP_AUTHORIZATION | TIME_MANAGEMENT;
  }
}
