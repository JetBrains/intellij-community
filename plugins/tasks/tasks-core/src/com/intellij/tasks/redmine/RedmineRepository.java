package com.intellij.tasks.redmine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 */
@Tag("Redmine")
public class RedmineRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.redmine.RedmineRepository");

  private Pattern myPattern;
  private String myAPIKey;
  private String myProjectId;

  @SuppressWarnings({"UnusedDeclaration"})
  public RedmineRepository() {}

  public RedmineRepository(RedmineRepositoryType type) {
    super(type);
  }

  public RedmineRepository(RedmineRepository other) {
    super(other);
    setAPIKey(other.myAPIKey);
    setProjectId(other.myProjectId);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 10, 0);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    @SuppressWarnings({"unchecked"}) List<Object> children = getIssues(query, max);
    List<Task> taskList = ContainerUtil.mapNotNull(children, new NullableFunction<Object, Task>() {
      public Task fun(Object o) {
        return createIssue((Element)o);
      }
    });

    return taskList.toArray(new Task[taskList.size()]);
  }

  @Nullable
  private Task createIssue(final Element element) {
    final String id = element.getChildText("id");
    if (id == null) {
      return null;
    }
    final String summary = element.getChildText("subject");
    if (summary == null) {
      return null;
    }
    final Element status = element.getChild("status");
    final boolean isClosed = status == null || "Closed".equals(status.getAttributeValue("name"));
    final String description = element.getChildText("description");
    final Ref<Date> updated = new Ref<Date>();
    final Ref<Date> created = new Ref<Date>();
    try {
      updated.set(parseDate(element, "updated_on"));
      created.set(parseDate(element, "created_on"));
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
        return id != null ? getUrl() + "/issues/" + id : null;
      }

      @NotNull
      @Override
      public String getId() {
        return myProjectId + "-" + id;
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
        return RedmineRepositoryType.ICON;
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
        return RedmineRepository.this;
      }

      @Override
      public String getPresentableName() {
        return getId() + ": " + getSummary();
      }
    };
  }

  private static Date parseDate(Element element, String name) throws ParseException {
    final String date = element.getChildText(name);
    if (date.matches(".*\\+\\d\\d:\\d\\d")) {
      final SimpleDateFormat format = new SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss", Locale.US);
      final int timeZoneIndex = date.length() - 6;
      format.setTimeZone(TimeZone.getTimeZone("GMT" + date.substring(timeZoneIndex)));
      return format.parse(date.substring(0, timeZoneIndex));
    }
    return (new SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)).parse(date);
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && !StringUtil.isEmpty(myProjectId);
  }

  @SuppressWarnings({"unchecked"})
  private List<Object> getIssues(String query, int max) throws Exception {
    String url = "/projects/" + myProjectId + "/issues.xml?";
    final boolean hasKey = !StringUtil.isEmpty(myAPIKey) && !isUseHttpAuthentication();
    if (hasKey) {
      url +="key=" + myAPIKey;
    }
    if (hasKey) url += "&";
    // getting only open id's
    url += encodeUrl("fields[]") + "=status_id&"  + 
           encodeUrl("operators[status_id]") + "=o&" +
           encodeUrl("values[status_id][]") + "=1";
    final boolean hasQuery = !StringUtil.isEmpty(query);
    if (hasQuery) {
      url += "&" + encodeUrl("fields[]") + "=subject&" +
             encodeUrl("operators[subject]") + "=" + encodeUrl("~") + "&" + 
             encodeUrl("values[subject][]") + "=" + encodeUrl(query);
    }
    if (max >= 0) {
      url += "&limit=" + encodeUrl(String.valueOf(max));
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

  private HttpMethod doREST(String request, boolean post) throws Exception {
    final HttpClient client = getHttpClient();
    client.getParams().setContentCharset("UTF-8");
    String uri = getUrl().replace("https://", EASY_HTTPS + "://") + request;
    HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    client.executeMethod(method);
    return method;
  }

  @Override
  public Task findTask(String id) throws Exception {
    final String realId = getRealId(id);
    if (realId == null) return null;
    HttpMethod method = doREST("/issues/" + realId + ".xml", false);
    InputStream stream = method.getResponseBodyAsStream();
    Element element = new SAXBuilder(false).build(stream).getRootElement();
    return element.getName().equals("issue") ? createIssue(element) : null;
  }

  @Override
  public BaseRepository clone() {
    return new RedmineRepository(this);
  }

  @Nullable
  private String getRealId(String id) {
    final String start = myProjectId + "-";
    return id.startsWith(start) ? id.substring(start.length()) : null;
  }

  @Nullable
  public String extractId(String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  public String getAPIKey() {
    return myAPIKey;
  }

  public void setAPIKey(String APIKey) {
    myAPIKey = APIKey;
  }

  public String getProjectId() {
    return myProjectId;
  }

  public void setProjectId(String projectId) {
    myProjectId = projectId;
    myPattern = Pattern.compile("(" + projectId + "\\-\\d+):\\s+");
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof RedmineRepository)) return false;

    RedmineRepository that = (RedmineRepository)o;
    if (getAPIKey() != null ? !getAPIKey().equals(that.getAPIKey()) : that.getAPIKey() != null) return false;
    if (getProjectId() != null ? !getProjectId().equals(that.getProjectId()) : that.getProjectId() != null) return false;
    return true;
  }


  @Override
  public String getPresentableName() {
    final String name = super.getPresentableName();
    return name +
           "/projects" +
           (!StringUtil.isEmpty(getProjectId()) ? "/" + getProjectId() : "");
  }
}
