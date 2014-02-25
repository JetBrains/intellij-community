package com.intellij.tasks.jira.soap;

import com.atlassian.connector.commons.jira.soap.axis.JiraSoapService;
import com.atlassian.connector.commons.jira.soap.axis.JiraSoapServiceServiceLocator;
import com.atlassian.theplugin.jira.api.JIRAIssueBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyValue;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.jira.JiraRemoteApi;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.apache.axis.AxisProperties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Legacy SOAP connector restored due to IDEA-120595.
 *
 * @author Mikhail Golubev
 */
public class JiraSoapApi extends JiraRemoteApi {

  private static final Logger LOG = Logger.getInstance(JiraSoapApi.class);
  private boolean myJira4 = true;

  public JiraSoapApi(@NotNull JiraRepository repository) {
    super(repository);
  }

  @NotNull
  @Override
  public List<Task> findTasks(String query, int max) throws Exception {
    StringBuilder url = new StringBuilder(myRepository.getUrl());
    url.append("/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?");
    url.append("tempMax=").append(max);
    url.append("&assignee=").append(TaskUtil.encodeUrl(myRepository.getUsername()));
    url.append("&reset=true");
    url.append("&sorter/field=updated");
    url.append("&sorter/order=DESC");
    url.append("&pager/start=0");
    return processRSS(url.toString(), login());
  }

  private List<Task> processRSS(String url, HttpClient client) throws Exception {
    GetMethod method = new GetMethod(url);
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
      List<Element> children = channel.getChildren("item");
      LOG.info("JIRA: " + children.size() + " issues found");
      return ContainerUtil.map(children, new Function<Element, Task>() {
        public Task fun(Element o) {
          return new JiraSoapTask(new JIRAIssueBean(myRepository.getUrl(), o, false), myRepository);
        }
      });
    }
    else {
      LOG.warn("JIRA channel not found");
    }
    return ContainerUtil.emptyList();
  }

  private HttpClient login() throws Exception {
    HttpClient client = myRepository.getHttpClient();
    client.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
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
        new JiraSoapServiceServiceLocator().getJirasoapserviceV2(new URL(myRepository.getUrl() + "/rpc/soap/jirasoapservice-v2"));
      if (myRepository.isUseProxy()) {
        final List<KeyValue<String, String>> list = HttpConfigurable.getJvmPropertiesList(false, null);
        if (!list.isEmpty()) {
          for (KeyValue<String, String> value : list) {
            AxisProperties.setProperty(value.getKey(), value.getValue());
          }
        }
      }

      soapService.login(myRepository.getUsername(), myRepository.getPassword());
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
    if (statusCode == HttpStatus.SC_OK && new String(postMethod.getResponseBody(2000)).contains("\"loginSucceeded\":false")) {
      throw new IOException(JiraRepository.LOGIN_FAILED_CHECK_YOUR_PERMISSIONS);
    }
    return true;
  }

  private PostMethod getLoginMethodFor4x() {
    String url = myRepository.getUrl() + "/rest/gadget/1.0/login";
    PostMethod postMethod = new PostMethod(url);
    postMethod.addParameter("os_username", myRepository.getUsername());
    postMethod.addParameter("os_password", myRepository.getPassword());
    postMethod.addParameter("os_destination", "/success");
    return postMethod;
  }

  @Nullable
  @Override
  public Task findTask(String key) throws Exception {
    try {
      StringBuilder url = new StringBuilder(myRepository.getUrl());
      url.append("/si/jira.issueviews:issue-xml/");
      url.append(key).append('/').append(key).append(".xml");

      List<Task> tasks = processRSS(url.toString(), login());
      return tasks.isEmpty() ? null : tasks.get(0);
    }
    catch (Exception e) {
      LOG.warn("Cannot get issue " + key + ": " + e.getMessage());
      return null;
    }
  }

  @NotNull
  @Override
  public final ApiType getType() {
    return ApiType.SOAP;
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
    throw new Exception("Task state cannot be updated in JIRA versions prior 4.2.");
  }

  @Override
  public void updateTimeSpend(LocalTask task, String timeSpent, String comment) throws Exception {
    throw new Exception("Time spent cannot be updated in JIRA versions prior 4.2.");
  }
}
