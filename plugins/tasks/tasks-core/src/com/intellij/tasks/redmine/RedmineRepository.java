package com.intellij.tasks.redmine;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.redmine.model.RedmineIssue;
import com.intellij.tasks.redmine.model.RedmineProject;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;
import static com.intellij.tasks.redmine.model.RedmineResponseWrapper.*;

/**
 * @author Mikhail Golubev
 * @author Dennis.Ushakov
 */
@Tag("Redmine")
public class RedmineRepository extends NewBaseRepositoryImpl {
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private static final Pattern ID_PATTERN = Pattern.compile("\\d+");
  private static final Logger LOG = Logger.getInstance(RedmineRepository.class);
  
  public static final RedmineProject UNSPECIFIED_PROJECT = new RedmineProject() {
    @NotNull
    @Override
    public String getName() {
      return "-- from all projects --";
    }

    @Nullable
    @Override
    public String getIdentifier() {
      return getName();
    }

    @Override
    public int getId() {
      return -1;
    }
  };

  private String myAPIKey = "";
  private RedmineProject myCurrentProject;
  private boolean myAssignedToMe = true;
  private List<RedmineProject> myProjects = null;

  /**
   * Serialization constructor
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public RedmineRepository() {
    // empty
  }

  /**
   * Normal instantiation constructor
   */
  public RedmineRepository(RedmineRepositoryType type) {
    super(type);
    setUseHttpAuthentication(true);
  }


  /**
   * Cloning constructor
   */
  public RedmineRepository(RedmineRepository other) {
    super(other);
    setAPIKey(other.myAPIKey);
    setCurrentProject(other.getCurrentProject());
    setAssignedToMe(other.isAssignedToMe());
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof RedmineRepository)) return false;
    RedmineRepository that = (RedmineRepository)o;
    if (!Comparing.equal(getAPIKey(), that.getAPIKey())) return false;
    if (!Comparing.equal(getCurrentProject(), that.getCurrentProject())) return false;
    if (isAssignedToMe() != that.isAssignedToMe()) return false;
    return true;
  }

  @NotNull
  @Override
  public RedmineRepository clone() {
    return new RedmineRepository(this);
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new NewBaseRepositoryImpl.HttpTestConnection(new HttpGet()) {
      @Override
      protected void test() throws Exception {
        // Strangely, Redmine doesn't return 401 or 403 error codes, if client sent wrong credentials, and instead
        // merely returns empty array of issues with status code of 200. This means that we should attempt to fetch
        // something more specific than issues to test proper configuration, e.g. current user information at
        // /users/current.json. Unfortunately this endpoint may be unavailable on some old servers (see IDEA-122845)
        // and in this case we have to come back to requesting issues in this case to test anything at all.

        URIBuilder uriBuilder = createUriBuilderWithApiKey("users", "current.json");
        myCurrentRequest.setURI(uriBuilder.build());
        HttpClient client = getHttpClient();

        HttpResponse httpResponse = client.execute(myCurrentRequest);
        StatusLine statusLine = httpResponse.getStatusLine();
        if (statusLine != null && statusLine.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
          // Check that projects can be downloaded via given URL and the latter is not project-specific
          myCurrentRequest = new HttpGet(getProjectsUrl(0, 1));
          statusLine = client.execute(myCurrentRequest).getStatusLine();
          if (statusLine != null && statusLine.getStatusCode() == HttpStatus.SC_OK) {
            myCurrentRequest = new HttpGet(getIssuesUrl(0, 1, true));
            statusLine = client.execute(myCurrentRequest).getStatusLine();
          }
        }
        if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
          throw RequestFailedException.forStatusCode(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        }
      }
    };
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    List<RedmineIssue> issues = fetchIssues(query, offset, limit, withClosed);
    List<Task> result = ContainerUtil.map(issues, issue -> new RedmineTask(this, issue));
    if (query != null && ID_PATTERN.matcher(query).matches()) {
      LOG.debug("Query '" + query + "' looks like an issue ID. Requesting it explicitly from the server " + this);
      final Task found = findTask(query);
      if (found != null) {
        result = ContainerUtil.append(result, found);
      }
    }
    return ArrayUtil.toObjectArray(result, Task.class);
  }

  public List<RedmineIssue> fetchIssues(String query, int offset, int limit, boolean withClosed) throws Exception {
    ensureProjectsDiscovered();
    // Legacy API, can't find proper documentation
    //if (StringUtil.isNotEmpty(query)) {
    //  builder.addParameter("fields[]", "subject").addParameter("operators[subject]", "~").addParameter("values[subject][]", query);
    //}
    HttpClient client = getHttpClient();
    HttpGet method = new HttpGet(getIssuesUrl(offset, limit, withClosed));
    IssuesWrapper wrapper = client.execute(method, new GsonSingleObjectDeserializer<>(GSON, IssuesWrapper.class));
    return wrapper == null ? Collections.<RedmineIssue>emptyList() : wrapper.getIssues();
  }

  private URI getIssuesUrl(int offset, int limit, boolean withClosed) throws URISyntaxException {
    URIBuilder builder = createUriBuilderWithApiKey("issues.json")
      .addParameter("offset", String.valueOf(offset))
      .addParameter("limit", String.valueOf(limit))
      .addParameter("sort", "updated_on:desc")
      .addParameter("status_id", withClosed ? "*" : "open");
    if (myAssignedToMe) {
      builder.addParameter("assigned_to_id", "me");
    }
    // If project was not chosen, all available issues still fetched. Such behavior may seems strange to user.
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      builder.addParameter("project_id", String.valueOf(myCurrentProject.getId()));
    }
    return builder.build();
  }

  public List<RedmineProject> fetchProjects() throws Exception {
    HttpClient client = getHttpClient();
    // Download projects with pagination (IDEA-125056, IDEA-125157)
    List<RedmineProject> allProjects = new ArrayList<>();
    int offset = 0;
    ProjectsWrapper wrapper;
    do {

      HttpGet method = new HttpGet(getProjectsUrl(offset, 50));
      wrapper = client.execute(method, new GsonSingleObjectDeserializer<>(GSON, ProjectsWrapper.class));
      offset += wrapper.getProjects().size();
      allProjects.addAll(wrapper.getProjects());
    }
    while (wrapper.getTotalCount() > allProjects.size() || wrapper.getProjects().isEmpty());

    myProjects = allProjects;
    return Collections.unmodifiableList(myProjects);
  }

  @NotNull
  private URI getProjectsUrl(int offset, int limit) throws URISyntaxException {
    URIBuilder builder = createUriBuilderWithApiKey("projects.json");
    builder.addParameter("offset", String.valueOf(offset));
    builder.addParameter("limit", String.valueOf(limit));
    return builder.build();
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    ensureProjectsDiscovered();
    HttpGet method = new HttpGet(createUriBuilderWithApiKey("issues", id + ".json").build());
    IssueWrapper wrapper = getHttpClient().execute(method, new GsonSingleObjectDeserializer<>(GSON, IssueWrapper.class, true));
    if (wrapper == null) {
      return null;
    }
    return new RedmineTask(this, wrapper.getIssue());
  }

  public String getAPIKey() {
    return myAPIKey;
  }

  public void setAPIKey(String APIKey) {
    myAPIKey = APIKey;
  }

  public boolean isAssignedToMe() {
    return myAssignedToMe;
  }

  public void setAssignedToMe(boolean assignedToMe) {
    this.myAssignedToMe = assignedToMe;
  }

  private boolean isUseApiKeyAuthentication() {
    return !isUseHttpAuthentication() && StringUtil.isNotEmpty(myAPIKey);
  }

  @NotNull
  private URIBuilder createUriBuilderWithApiKey(@NotNull Object... pathParts) throws URISyntaxException {
    final URIBuilder builder = new URIBuilder(getRestApiUrl(pathParts));
    if (isUseApiKeyAuthentication()) {
      builder.addParameter("key", myAPIKey);
    }
    return builder;
  }

  @Override
  public String getPresentableName() {
    String name = super.getPresentableName();
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      name += "/projects/" + StringUtil.notNullize(myCurrentProject.getIdentifier(), String.valueOf(myCurrentProject.getId()));
    }
    return name;
  }

  @Override
  public boolean isConfigured() {
    if (!super.isConfigured()) return false;
    if (isUseHttpAuthentication()) {
      return StringUtil.isNotEmpty(myPassword) && StringUtil.isNotEmpty(myUsername);
    }
    return StringUtil.isNotEmpty(myAPIKey);
  }

  @Nullable
  @Override
  public String extractId(@NotNull String taskName) {
    return ID_PATTERN.matcher(taskName).matches() ? taskName : null;
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() & ~NATIVE_SEARCH | BASIC_HTTP_AUTHORIZATION;
  }

  @Nullable
  public RedmineProject getCurrentProject() {
    return myCurrentProject;
  }

  public void setCurrentProject(@Nullable RedmineProject project) {
    myCurrentProject = project != null && project.getId() == -1 ? UNSPECIFIED_PROJECT : project;
  }

  @NotNull
  public List<RedmineProject> getProjects() {
    try {
      ensureProjectsDiscovered();
    }
    catch (Exception ignored) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(myProjects);
  }

  private void ensureProjectsDiscovered() throws Exception {
    if (myProjects == null) {
      fetchProjects();
    }
  }

  @TestOnly
  @Transient
  public void setProjects(@NotNull List<RedmineProject> projects) {
    myProjects = projects;
  }
}
