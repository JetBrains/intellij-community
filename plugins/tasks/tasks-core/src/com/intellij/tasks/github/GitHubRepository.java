package com.intellij.tasks.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.pivotal.PivotalTrackerRepository;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
@Tag("GitHub")
public class GitHubRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.github.GitHubRepository");
  private static final String API_URL = "/api/v2/xml";

  private Pattern myPattern;
  private String myRepoAuthor;
  private String myRepoName;

  private static final String GITHUB_HOST = "https://github.com";

  {
    setUrl(GITHUB_HOST);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public GitHubRepository() {}

  public GitHubRepository(GitHubRepository other) {
    super(other);
    setRepoName(other.myRepoName);
    setRepoAuthor(other.myRepoAuthor);
  }

  public GitHubRepository(GitHubRepositoryType type) {
    super(type);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 10, 0);
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           StringUtil.isNotEmpty(getRepoName());
  }

  @Override
  public String getPresentableName() {
    final String name = super.getPresentableName();
    return name +
           (!StringUtil.isEmpty(getRepoAuthor()) ? "/" + getRepoAuthor() : "") +
           (!StringUtil.isEmpty(getRepoName()) ? "/" + getRepoName() : "");
  }

  private HttpMethod doREST(String request, boolean post) throws Exception {
    final HttpClient client = getHttpClient();
    client.getParams().setContentCharset("UTF-8");
    String uri = getUrl() + request;
    HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    client.executeMethod(method);
    return method;
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    @SuppressWarnings({"unchecked"}) List<Object> children = getIssues(query);
    List<Task> taskList = ContainerUtil.mapNotNull(children, new NullableFunction<Object, Task>() {
      public Task fun(Object o) {
        return createIssue((Element)o);
      }
    });

    return taskList.toArray(new Task[taskList.size()]);
  }

  @Override
  public String getUrl() {
    return GITHUB_HOST;
  }

  private List getIssues(String query) throws Exception {
    String url;
    if (!StringUtil.isEmpty(query)) {
      url = buildUrl("/search/") + "/open";
      url += encodeUrl(query);
    } else {
      url = buildUrl("/list/") + "/open";
    }
    HttpMethod method = doREST(url, false);
    InputStream stream = method.getResponseBodyAsStream();
    Element element = new SAXBuilder(false).build(stream).getRootElement();

    if (!"issues".equals(element.getName())) {
      LOG.warn("Error fetching issues for: " + url + ", HTTP status code: " + method.getStatusCode());
      throw new Exception("Error fetching issues for: " + url + ", HTTP status code: " + method.getStatusCode() +
                          "\n" + element.getText());
    }

    return element.getChildren("issue");
  }

  @Nullable
  private Task createIssue(final Element element) {
    final String id = element.getChildText("number");
    if (id == null) {
      return null;
    }
    final String summary = element.getChildText("title");
    if (summary == null) {
      return null;
    }
    final boolean isClosed = !"open".equals(element.getChildText("state"));
    final String description = element.getChildText("body");
    final Ref<Date> updated = new Ref<Date>();
    final Ref<Date> created = new Ref<Date>();
    try {
      updated.set(PivotalTrackerRepository.parseDate(element, "updated-at"));
      created.set(PivotalTrackerRepository.parseDate(element, "created-at"));
    } catch (ParseException e) {
      LOG.warn(e);
    }

    return new Task() {
      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        final String id = getRealId(getId());
        return id != null ? getUrl() + "/" + getRepoAuthor() + "/" + myRepoName + "/issues/issue/" + id : null;
      }

      @NotNull
      @Override
      public String getId() {
        return myRepoName + "-" + id;
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
        return new Comment[0];
      }

      @Override
      public Icon getIcon() {
        return GitHubRepositoryType.ICON;
      }

      @NotNull
      @Override
      public TaskType getType() {
        return TaskType.BUG;
      }

      @Override
      public Date getUpdated() {
        return updated.get();
      }

      @Override
      public Date getCreated() {
        return created.get();
      }

      @Override
      public boolean isClosed() {
        return isClosed;
      }

      @Override
      public TaskRepository getRepository() {
        return GitHubRepository.this;
      }

      @Override
      public String getPresentableName() {
        return getId() + ": " + getSummary();
      }
    };
  }

  @Nullable
  private String getRealId(String id) {
    final String start = myRepoName + "-";
    return id.startsWith(start) ? id.substring(start.length()) : null;
  }

  @Nullable
  public String extractId(String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  @Override
  public Task findTask(String id) throws Exception {
    final String realId = getRealId(id);
    if (realId == null) return null;
    HttpMethod method = doREST(buildUrl("/show/") + "/" + realId, false);
    InputStream stream = method.getResponseBodyAsStream();
    Element element = new SAXBuilder(false).build(stream).getRootElement();
    return element.getName().equals("issue") ? createIssue(element) : null;
  }

  private String buildUrl(final String start) {
    return API_URL + "/issues" + start + getRepoAuthor() + "/" + myRepoName;
  }

  @Override
  public BaseRepository clone() {
    return new GitHubRepository(this);
  }

  public String getRepoName() {
    return myRepoName;
  }

  public void setRepoName(String repoName) {
    myRepoName = repoName;
    myPattern = Pattern.compile("(" + repoName + "\\-\\d+):\\s+");
  }

  public String getRepoAuthor() {
    return !StringUtil.isEmpty(myRepoAuthor) ? myRepoAuthor : getUsername();
  }

  public void setRepoAuthor(String repoAuthor) {
    myRepoAuthor = repoAuthor;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof GitHubRepository)) return false;

    GitHubRepository that = (GitHubRepository)o;
    if (getRepoName() != null ? !getRepoName().equals(that.getRepoName()) : that.getRepoName() != null) return false;
    return true;
  }
}
