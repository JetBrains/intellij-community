package com.intellij.tasks.gitlab;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonMultipleObjectsDeserializer;
import static com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;

/**
 * @author Mikhail Golubev
 */
@Tag("Gitlab")
public class GitlabRepository extends NewBaseRepositoryImpl {

  @NonNls public static final String REST_API_PATH_PREFIX = "/api/v3/";
  @NonNls private static final String TOKEN_HEADER = "PRIVATE-TOKEN";

  private static final Pattern ID_PATTERN = Pattern.compile("\\d+");
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();

  // @formatter:off
  private static final TypeToken<List<GitlabProject>> LIST_OF_PROJECTS_TYPE = new TypeToken<List<GitlabProject>>() {};
  private static final TypeToken<List<GitlabIssue>> LIST_OF_ISSUES_TYPE = new TypeToken<List<GitlabIssue>>() {};
  // @formatter:on

  static final GitlabProject UNSPECIFIED_PROJECT = new GitlabProject() {
    @Override
    public String getName() {
      return "-- all issues created by you --";
    }

    @Override
    public int getId() {
      return -1;
    }
  };
  private GitlabProject myCurrentProject;
  private List<GitlabProject> myProjects = null;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public GitlabRepository() {
  }

  /**
   * Normal instantiation constructor
   */
  public GitlabRepository(TaskRepositoryType type) {
    super(type);
  }

  /**
   * Cloning constructor
   */
  public GitlabRepository(GitlabRepository other) {
    super(other);
    myCurrentProject = other.myCurrentProject;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    final GitlabRepository repository = (GitlabRepository)o;
    if (!Comparing.equal(myCurrentProject, repository.myCurrentProject)) return false;
    return true;
  }

  @NotNull
  @Override
  public GitlabRepository clone() {
    return new GitlabRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    final List<GitlabIssue> issues = fetchIssues((offset / limit) + 1, limit, !withClosed);
    return ContainerUtil.map2Array(issues, GitlabTask.class, issue -> new GitlabTask(this, issue));
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    // doesn't work now, because Gitlab's REST API doesn't provide endpoint to find task
    // using only its global ID, it requires both task's global ID AND task's project ID
    return null;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new HttpTestConnection(new HttpGet(getIssuesUrl()));
  }

  /**
   * Always forcibly attempts do fetch new projects from server.
   */
  @NotNull
  public List<GitlabProject> fetchProjects() throws Exception {
    final ResponseHandler<List<GitlabProject>> handler = new GsonMultipleObjectsDeserializer<>(GSON, LIST_OF_PROJECTS_TYPE);
    final String projectUrl = getRestApiUrl("projects");
    final List<GitlabProject> result = new ArrayList<>();
    int pageNum = 1;
    while (true) {
      final URI paginatedProjectsUrl = new URIBuilder(projectUrl)
        .addParameter("page", String.valueOf(pageNum))
        .addParameter("per_page", "30")
        .build();
      final List<GitlabProject> page = getHttpClient().execute(new HttpGet(paginatedProjectsUrl), handler);
      // Gitlab's REST API doesn't allow to know beforehand how many projects are available
      if (page.isEmpty()) {
        break;
      }
      result.addAll(page);
      pageNum++;
    }
    myProjects = result;
    return Collections.unmodifiableList(myProjects);
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public GitlabProject fetchProject(int id) throws Exception {
    final HttpGet request = new HttpGet(getRestApiUrl("project", id));
    return getHttpClient().execute(request, new GsonSingleObjectDeserializer<>(GSON, GitlabProject.class));
  }

  @NotNull
  public List<GitlabIssue> fetchIssues(int pageNumber, int pageSize, boolean openedOnly) throws Exception {
    ensureProjectsDiscovered();
    final URIBuilder uriBuilder = new URIBuilder(getIssuesUrl())
      .addParameter("page", String.valueOf(pageNumber))
      .addParameter("per_page", String.valueOf(pageSize))
      // Ordering was added in v7.8
      .addParameter("order_by", "updated_at");
    if (openedOnly) {
      // Filtering by state was added in v7.3
      uriBuilder.addParameter("state", "opened");
    }
    final ResponseHandler<List<GitlabIssue>> handler = new GsonMultipleObjectsDeserializer<>(GSON, LIST_OF_ISSUES_TYPE);
    return getHttpClient().execute(new HttpGet(uriBuilder.build()), handler);
  }

  private String getIssuesUrl() {
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      return getRestApiUrl("projects", myCurrentProject.getId(), "issues");
    }
    return getRestApiUrl("issues");
  }

  /**
   * @param issueId global issue's ID (<tt>id</tt> field, not <tt>iid</tt>)
   */
  @Nullable
  public GitlabIssue fetchIssue(int projectId, int issueId) throws Exception {
    ensureProjectsDiscovered();
    final HttpGet request = new HttpGet(getRestApiUrl("projects", projectId, "issues", issueId));
    final ResponseHandler<GitlabIssue> handler = new GsonSingleObjectDeserializer<>(GSON, GitlabIssue.class, true);
    return getHttpClient().execute(request, handler);
  }

  @Override
  public String getPresentableName() {
    String name = getUrl();
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      name += "/" + myCurrentProject.getName();
    }
    return name;
  }

  @Nullable
  @Override
  public String extractId(@NotNull String taskName) {
    return ID_PATTERN.matcher(taskName).matches() ? taskName : null;
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && !myPassword.isEmpty();
  }

  @NotNull
  @Override
  public String getRestApiPathPrefix() {
    return REST_API_PATH_PREFIX;
  }

  @Nullable
  @Override
  protected HttpRequestInterceptor createRequestInterceptor() {
    return new HttpRequestInterceptor() {
      @Override
      public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        request.addHeader(TOKEN_HEADER, myPassword);
        //request.addHeader("Accept", "application/json");
      }
    };
  }

  public void setCurrentProject(@Nullable GitlabProject project) {
    myCurrentProject = project != null && project.getId() == -1 ? UNSPECIFIED_PROJECT : project;
  }

  public GitlabProject getCurrentProject() {
    return myCurrentProject;
  }

  /**
   * May return cached projects or make request to receive new ones.
   */
  @NotNull
  public List<GitlabProject> getProjects() {
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
  public void setProjects(@NotNull List<GitlabProject> projects) {
    myProjects = projects;
  }
}
