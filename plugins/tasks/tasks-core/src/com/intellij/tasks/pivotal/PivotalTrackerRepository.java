package com.intellij.tasks.pivotal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.SimpleComment;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dennis.Ushakov
 *
 * TODO: update to REST APIv5
 */
@Tag("PivotalTracker")
public class PivotalTrackerRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.pivotal.PivotalTrackerRepository");
  private static final String API_URL = "/services/v3";

  private Pattern myPattern;
  private String myProjectId;
  private String myAPIKey;

  //private boolean myTasksSupport = false;

  {
    if (StringUtil.isEmpty(getUrl())) {
      setUrl("https://www.pivotaltracker.com");
    }
  }

  /** for serialization */
  @SuppressWarnings({"UnusedDeclaration"})
  public PivotalTrackerRepository() {
    myCommitMessageFormat = "[fixes #{number}] {summary}";
  }

  public PivotalTrackerRepository(final PivotalTrackerRepositoryType type) {
    super(type);
  }

  private PivotalTrackerRepository(final PivotalTrackerRepository other) {
    super(other);
    setProjectId(other.myProjectId);
    setAPIKey(other.myAPIKey);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 10, 0);
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           StringUtil.isNotEmpty(getProjectId()) &&
           StringUtil.isNotEmpty(getAPIKey());
  }

  @Override
  public Task[] getIssues(@Nullable final String query, final int max, final long since) throws Exception {
    List<Element> children = getStories(query, max);

    final List<Task> tasks = ContainerUtil.mapNotNull(children, (NullableFunction<Element, Task>)o -> createIssue(o));
    return tasks.toArray(new Task[tasks.size()]);
  }

  private List<Element> getStories(@Nullable final String query, final int max) throws Exception {
    String url = API_URL + "/projects/" + myProjectId + "/stories";
    url += "?filter=" + encodeUrl("state:started,unstarted,unscheduled,rejected");
    if (!StringUtil.isEmpty(query)) {
      url += encodeUrl(" \"" + query + '"');
    }
    if (max >= 0) {
      url += "&limit=" + encodeUrl(String.valueOf(max));
    }
    LOG.info("Getting all the stories with url: " + url);
    final HttpMethod method = doREST(url, HTTPMethod.GET);
    final InputStream stream = method.getResponseBodyAsStream();
    final Element element = new SAXBuilder(false).build(stream).getRootElement();

    if (!"stories".equals(element.getName())) {
      LOG.warn("Error fetching issues for: " + url + ", HTTP status code: " + method.getStatusCode());
      throw new Exception("Error fetching issues for: " + url + ", HTTP status code: " + method.getStatusCode() +
                          "\n" + element.getText());
    }

    return element.getChildren("story");
  }

  @Nullable
  private Task createIssue(final Element element) {
    final String id = element.getChildText("id");
    if (id == null) {
      return null;
    }
    final String summary = element.getChildText("name");
    if (summary == null) {
      return null;
    }
    final String type = element.getChildText("story_type");
    if (type == null) {
      return null;
    }
    final Comment[] comments = parseComments(element.getChild("notes"));
    final boolean isClosed = "accepted".equals(element.getChildText("state")) ||
                             "delivered".equals(element.getChildText("state")) ||
                             "finished".equals(element.getChildText("state"));
    final String description = element.getChildText("description");
    final Ref<Date> updated = new Ref<>();
    final Ref<Date> created = new Ref<>();
    try {
      updated.set(parseDate(element, "updated_at"));
      created.set(parseDate(element, "created_at"));
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
        return id != null ? getUrl() + "/story/show/" + id : null;
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
        return comments;
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return IconLoader.getIcon(getCustomIcon(), PivotalTrackerRepository.class);
      }

      @NotNull
      @Override
      public TaskType getType() {
        return TaskType.OTHER;
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
        return PivotalTrackerRepository.this;
      }

      @Override
      public String getPresentableName() {
        return getId() + ": " + getSummary();
      }

      @NotNull
      @Override
      public String getCustomIcon() {
        return "/icons/pivotal/" + type + ".png";
      }
    };
  }

  private static Comment[] parseComments(Element notes) {
    if (notes == null) return Comment.EMPTY_ARRAY;
    final List<Comment> result = new ArrayList<>();
    //noinspection unchecked
    for (Element note : (List<Element>)notes.getChildren("note")) {
      final String text = note.getChildText("text");
      if (text == null) continue;
      final Ref<Date> date = new Ref<>();
      try {
        date.set(parseDate(note, "noted_at"));
      } catch (ParseException e) {
        LOG.warn(e);
      }
      final String author = note.getChildText("author");
      result.add(new SimpleComment(date.get(), author, text));
    }
    return result.toArray(new Comment[result.size()]);
  }

  @Nullable
  private static Date parseDate(final Element element, final String name) throws ParseException {
    String date = element.getChildText(name);
    return TaskUtil.parseDate(date);
  }

  private HttpMethod doREST(final String request, final HTTPMethod type) throws Exception {
    final HttpClient client = getHttpClient();
    client.getParams().setContentCharset("UTF-8");
    final String uri = getUrl() + request;
    final HttpMethod method = type == HTTPMethod.POST ? new PostMethod(uri) :
                              type == HTTPMethod.PUT ? new PutMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    client.executeMethod(method);
    return method;
  }

  @Nullable
  @Override
  public Task findTask(@NotNull final String id) throws Exception {
    final String realId = getRealId(id);
    if (realId == null) return null;
    final String url = API_URL + "/projects/" + myProjectId + "/stories/" + realId;
    LOG.info("Retrieving issue by id: " + url);
    final HttpMethod method = doREST(url, HTTPMethod.GET);
    final InputStream stream = method.getResponseBodyAsStream();
    final Element element = new SAXBuilder(false).build(stream).getRootElement();
    return element.getName().equals("story") ? createIssue(element) : null;
  }

  @Nullable
  private String getRealId(final String id) {
    final String[] split = id.split("\\-");
    final String projectId = split[0];
    return Comparing.strEqual(projectId, myProjectId) ? split[1] : null;
  }

  @Nullable
  public String extractId(@NotNull final String taskName) {
    Matcher matcher = myPattern.matcher(taskName);
    return matcher.find() ? matcher.group(1) : null;
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new PivotalTrackerRepository(this);
  }

  @Override
  protected void configureHttpMethod(final HttpMethod method) {
    method.addRequestHeader("X-TrackerToken", myAPIKey);
    //method.setFollowRedirects(true);
  }

  public String getProjectId() {
    return myProjectId;
  }
  
  public void setProjectId(final String projectId) {
    myProjectId = projectId;
    myPattern = Pattern.compile("(" + projectId + "\\-\\d+):\\s+");
  }

  public String getAPIKey() {
    return myAPIKey;
  }

  public void setAPIKey(final String APIKey) {
    myAPIKey = APIKey;
  }

  @Override
  public String getPresentableName() {
    final String name = super.getPresentableName();
    return name + (!StringUtil.isEmpty(getProjectId()) ? "/" + getProjectId() : "");
  }

  @Nullable
  @Override
  public String getTaskComment(@NotNull final Task task) {
    if (isShouldFormatCommitMessage()) {
      final String id = task.getId();
      final String realId = getRealId(id);
      return realId != null ?
             myCommitMessageFormat.replace("{id}", realId).replace("{project}", myProjectId) + " " + task.getSummary() :
             null;
    }
    return super.getTaskComment(task);
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull TaskState state) throws Exception {
    final String realId = getRealId(task.getId());
    if (realId == null) return;
    final String stateName;
    switch (state) {
      case IN_PROGRESS:
        stateName = "started";
        break;
      case RESOLVED:
        stateName = "finished";
        break;
      // may add some others in future
      default:
        return;
    }
    String url = API_URL + "/projects/" + myProjectId + "/stories/" + realId;
    url += "?" + encodeUrl("story[current_state]") + "=" + encodeUrl(stateName);
    LOG.info("Updating issue state by id: " + url);
    final HttpMethod method = doREST(url, HTTPMethod.PUT);
    final InputStream stream = method.getResponseBodyAsStream();
    final Element element = new SAXBuilder(false).build(stream).getRootElement();
    if (!element.getName().equals("story")) {
      if (element.getName().equals("errors")) {
        throw new Exception(extractErrorMessage(element));
      } else {
        // unknown error, probably our fault
        LOG.warn("Error setting state for: " + url + ", HTTP status code: " + method.getStatusCode());
        throw new Exception(String.format("Cannot set state '%s' for issue.", stateName));
      }
    }
  }

  @NotNull
  private static String extractErrorMessage(@NotNull Element element) {
    return StringUtil.notNullize(element.getChild("error").getText());
  }

  @Override
  public boolean equals(final Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof PivotalTrackerRepository)) return false;

    final PivotalTrackerRepository that = (PivotalTrackerRepository)o;
    if (getAPIKey() != null ? !getAPIKey().equals(that.getAPIKey()) : that.getAPIKey() != null) return false;
    if (getProjectId() != null ? !getProjectId().equals(that.getProjectId()) : that.getProjectId() != null) return false;
    if (getCommitMessageFormat() != null ? !getCommitMessageFormat().equals(that.getCommitMessageFormat()) : that.getCommitMessageFormat() != null) return false;
    return isShouldFormatCommitMessage() == that.isShouldFormatCommitMessage();
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | BASIC_HTTP_AUTHORIZATION | STATE_UPDATING;
  }

  @Override
  public void setUrl(String url) {
    if (url.startsWith("http:")) {
      url = "https:" + StringUtil.trimStart(url, "http:");
    }
    super.setUrl(url);
  }
}
