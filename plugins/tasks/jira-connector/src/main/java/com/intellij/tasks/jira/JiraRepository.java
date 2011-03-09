package com.intellij.tasks.jira;

import com.atlassian.theplugin.jira.api.JIRAIssueBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcClientException;
import org.apache.xmlrpc.XmlRpcTransport;
import org.apache.xmlrpc.XmlRpcTransportFactory;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@Tag("JIRA")
public class JiraRepository extends BaseRepositoryImpl implements XmlRpcTransportFactory {

  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.jira.JiraRepository");

  private String myServerVersion;
  private static final String JIRA1 = "jira1.";
  private static final String XMLRPC_PATH = "/rpc/xmlrpc";

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
    StringBuilder url = new StringBuilder(getUrl());
    String version = getServerVersion();
    if (version == null || StringUtil.compareVersionNumbers(myServerVersion, "3.7") < 0) {
      url.append("/secure/IssueNavigator.jspa?view=rss&decorator=none&");
    }
    else {
      url.append("/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?");
    }
    url.append("tempMax=").append(max);
    url.append("&assignee=").append(encodeUrl(getUsername()));
//      url.append("&resolution=-1");
    url.append("&reset=true");
    url.append("&sorter/field=").append("updated");
    url.append("&sorter/order=").append("DESC");
    url.append("&pager/start=").append(0);
    appendAuthentication(url, false);

    return processRSS(url.toString());
  }

  @Override
  public void setTaskState(Task task, TaskState state) throws Exception {
    updateIssue(task.getId(), "status", state.name());
  }

  private Task[] processRSS(String url) throws IOException, JDOMException {
    HttpClient client = getHttpClient();
    GetMethod method = new GetMethod(url);
    configureHttpMethod(method);
    client.executeMethod(method);

    int code = method.getStatusCode();
    if (code != HttpStatus.SC_OK) {
      throw new IOException("HTTP " + code + " (" + HttpStatus.getStatusText(code) + ") " + method.getStatusText());
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

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private void updateIssue(String issueKey, String field, String value) throws Exception {

    Hashtable<Object, Object> hashtable = new Hashtable<Object, Object>();
//    Hashtable<Object, Object> hashtable = new Hashtable<Object, Object>();
    hashtable.put(field, "3");
    doXmlRpcRequest("updateIssue", issueKey, hashtable);
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private Object doXmlRpcRequest(String command, Object... params) throws Exception {

    URL url = new URL(getUrl() + XMLRPC_PATH);

    XmlRpcClient rpcClient = new XmlRpcClient(url, this);

    Vector<Object> loginParams = new Vector<Object>(2);
    loginParams.add(getUsername());
    loginParams.add(getPassword());
    String loginToken = (String)rpcClient.execute(JIRA1 + "login", loginParams);
    if (loginToken == null) {
      throw new Exception("Cannot connect to " + getUrl());
    }
    Vector<Object> tokens = new Vector<Object>();
    tokens.add(loginToken);
    ContainerUtil.addAll(tokens, params);

    try {
      return rpcClient.execute(JIRA1 + command, tokens);
    }
    finally {
      rpcClient.execute(JIRA1 + "logout", new Vector<Object>(Arrays.asList(loginToken)));
    }
  }

  private String getServerVersion() throws Exception {
    if (myServerVersion == null) {
      try {
        Map map = (Map)doXmlRpcRequest("getServerInfo");
        myServerVersion = (String)map.get("version");
      }
      catch (Exception e) {

        myServerVersion = "3.8";
        LOG.warn(e);
        if (e.getMessage().contains("Authentication")) {
          throw e;
        }
      }
    }
    return myServerVersion;
  }

  @Override
  public CancellableConnection createCancellableConnection() {
    final HttpClient client = getHttpClient();
    GetMethod method = new GetMethod(getUrl() + XMLRPC_PATH);
    configureHttpMethod(method);
    return new HttpTestConnection(method) {

      @Override
      public void doTest(HttpMethod method) throws Exception {
        try {
          client.executeMethod(method);
          Element root = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getRootElement();
          if (!root.getName().equals("methodResponse")) {
            throw new Exception(root.getName());
          }
        }
        catch (Exception e) {
          LOG.warn(e);
          throw new Exception("JIRA RPC plugin not responded");
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
      appendAuthentication(url, true);

      Task[] tasks = processRSS(url.toString());
      return tasks.length == 0 ? null : tasks[0];
    }
    catch (Exception e) {
      LOG.warn("Cannot get issue " + id + ": " + e.getMessage());
      return null;
    }
  }

  private void appendAuthentication(StringBuilder builder, boolean firstParam) {
    builder.append(firstParam ? '?' : '&');
    if (!isUseHttpAuthentication()) {
      builder.append("os_username=").append(encodeUrl(getUsername()));
      builder.append("&os_password=").append(encodeUrl(getPassword()));
    }
    else {
      builder.append("os_authType=basic");
    }
  }

  public XmlRpcTransport createTransport() throws XmlRpcClientException {
    return new HttpClientTransport(getUrl() + XMLRPC_PATH, getHttpClient()) {
      @Override
      protected void configureMethod(HttpMethod method) {
        configureHttpMethod(method);
      }
    };
  }

  public void setProperty(String propertyName, Object value) {
    
  }
}
