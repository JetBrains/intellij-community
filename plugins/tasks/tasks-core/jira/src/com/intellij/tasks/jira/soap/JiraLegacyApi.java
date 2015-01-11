package com.intellij.tasks.jira.soap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.jira.JiraRemoteApi;
import com.intellij.tasks.jira.JiraRepository;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Legacy integration restored due to IDEA-120595.
 *
 * @author Mikhail Golubev
 */
public class JiraLegacyApi extends JiraRemoteApi {

  private static final Logger LOG = Logger.getInstance(JiraLegacyApi.class);

  @NonNls private static final String RSS_SEARCH_PATH = "/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml";
  public static final String RSS_ISSUE_PATH = "/si/jira.issueviews:issue-xml/";

  public JiraLegacyApi(@NotNull JiraRepository repository) {
    super(repository);
  }

  @NotNull
  @Override
  public List<Task> findTasks(@NotNull String query, int max) throws Exception {

    // Unfortunately, both SOAP and XML-RPC interfaces of JIRA don't allow fetching *all* tasks from server, but
    // only filtered by some search term (see http://stackoverflow.com/questions/764282/how-can-jira-soap-api-not-have-this-method).
    // JQL was added in SOAP only since JIRA 4.0 (see method JiraSoapService#getIssuesFromJqlSearch() at
    // https://docs.atlassian.com/software/jira/docs/api/rpc-jira-plugin/latest/index.html?com/atlassian/jira/rpc/soap/JiraSoapService.html)
    // So due to this limitation and the need to support these old versions of bug tracker (3.0, 4.2) we need the following ugly and hacky
    // solution with extracting issues from RSS feed.

    GetMethod method = new GetMethod(myRepository.getUrl() + RSS_SEARCH_PATH);
    method.setQueryString(new NameValuePair[] {
      new NameValuePair("tempMax", String.valueOf(max)),
      new NameValuePair("assignee", TaskUtil.encodeUrl(myRepository.getUsername())),
      new NameValuePair("reset", "true"),
      new NameValuePair("sorter/field", "updated"),
      new NameValuePair("sorter/order", "DESC"),
      new NameValuePair("pager/start", "0")
    });
    return processRSS(method);
  }

  private List<Task> processRSS(@NotNull GetMethod method) throws Exception {
    // Basic authorization should be enough
    int code = myRepository.getHttpClient().executeMethod(method);
    if (code != HttpStatus.SC_OK) {
      throw new Exception(TaskBundle.message("failure.http.error", code, method.getStatusText()));
    }
    Element root = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getRootElement();
    Element channel = root.getChild("channel");
    if (channel != null) {
      List<Element> children = channel.getChildren("item");
      LOG.debug("Total issues in JIRA RSS feed: " + children.size());
      return ContainerUtil.map(children, new Function<Element, Task>() {
        public Task fun(Element element) {
          return new JiraSoapTask(element, myRepository);
        }
      });
    }
    else {
      LOG.warn("JIRA channel not found");
    }
    return ContainerUtil.emptyList();
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String key) throws Exception {
    try {
      List<Task> tasks = processRSS(new GetMethod(myRepository.getUrl() + RSS_ISSUE_PATH + key + '/' + key + ".xml"));
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
    return ApiType.LEGACY;
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    return Collections.emptySet();
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    throw new Exception(TaskBundle.message("jira.failure.no.state.update"));
  }

  @Override
  public void updateTimeSpend(@NotNull LocalTask task, @NotNull String timeSpent, String comment) throws Exception {
    throw new Exception(TaskBundle.message("jira.failure.no.time.spent"));
  }
}
