package com.intellij.tasks.redmine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.gson.GsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.redmine.model.RedmineIssue;
import com.intellij.tasks.redmine.model.RedmineProject;
import com.intellij.tasks.redmine.model.RedmineResponseWrapper;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.tasks.impl.httpclient.ResponseUtil.GsonSingleObjectDeserializer;
import static com.intellij.tasks.redmine.model.RedmineResponseWrapper.IssueWrapper;
import static com.intellij.tasks.redmine.model.RedmineResponseWrapper.ProjectsWrapper;

/**
 * @author Mikhail Golubev
 * @author Dennis.Ushakov
 */
@Tag("Redmine")
public class RedmineRepository extends NewBaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance(RedmineRepository.class);
  private static final Gson GSON = GsonUtil.createDefaultBuilder().create();

  // Type tokens
  private static final TypeToken<List<RedmineProject>> LIST_OF_PROJECTS_TYPE = new TypeToken<List<RedmineProject>>() {
  };
  private static final TypeToken<List<RedmineIssue>> LIST_OF_ISSUES_TYPE = new TypeToken<List<RedmineIssue>>() {
  };

  public static final RedmineProject UNSPECIFIED_PROJECT = new RedmineProject() {
    @NotNull
    @Override
    public String getName() {
      return "-- from all projects --";
    }

    @Override
    public int getId() {
      return -1;
    }
  };

  private String myAPIKey = "";
  private RedmineProject myCurrentProject;

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
  }


  /**
   * Cloning constructor
   */
  public RedmineRepository(RedmineRepository other) {
    super(other);
    setAPIKey(other.myAPIKey);
    setCurrentProject(other.getCurrentProject());
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof RedmineRepository)) return false;
    RedmineRepository that = (RedmineRepository)o;
    if (!Comparing.equal(getAPIKey(), that.getAPIKey())) return false;
    if (!Comparing.equal(getCurrentProject(), that.getCurrentProject())) return false;
    return true;
  }

  @Override
  public RedmineRepository clone() {
    return new RedmineRepository(this);
  }

  @Override
  public void testConnection() throws Exception {
    getIssues("", 0, 1, true);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    List<RedmineIssue> issues = fetchIssues(query, offset, limit, withClosed);
    return ContainerUtil.map2Array(issues, RedmineTask.class, new Function<RedmineIssue, RedmineTask>() {
      @Override
      public RedmineTask fun(RedmineIssue issue) {
        return new RedmineTask(issue, RedmineRepository.this);
      }
    });
  }

  public List<RedmineIssue> fetchIssues(String query, int offset, int limit, boolean withClosed) throws Exception {
    URIBuilder builder = new URIBuilder(getRestApiUrl("issues.json"))
      .addParameter("offset", String.valueOf(offset))
      .addParameter("limit", String.valueOf(limit))
      .addParameter("status_is", withClosed ? "*" : "open");
    if (StringUtil.isNotEmpty(query)) {
      builder.addParameter("fields[]", "subject").addParameter("operators[subject]", "~").addParameter("values[subject][]", query);
    }
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      builder.addParameter("project_id", String.valueOf(myCurrentProject.getId()));
    }
    if (useApiKeyAuthentication()) {
      builder.addParameter("key", myAPIKey);
    }
    HttpClient client = getHttpClient();
    HttpGet method = new HttpGet(builder.toString());
    return client.execute(method, new GsonSingleObjectDeserializer<RedmineResponseWrapper.IssuesWrapper>(GSON, RedmineResponseWrapper.IssuesWrapper.class)).getIssues();
  }

  public List<RedmineProject> fetchProjects() throws Exception {
    URIBuilder builder = new URIBuilder(getRestApiUrl("projects.json"));
    if (useApiKeyAuthentication()) {
      builder.addParameter("key", myAPIKey);
    }
    HttpClient client = getHttpClient();
    HttpGet method = new HttpGet(builder.toString());
    return client.execute(method, new GsonSingleObjectDeserializer<ProjectsWrapper>(GSON, RedmineResponseWrapper.ProjectsWrapper.class)).getProjects();
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    HttpGet method = new HttpGet(getRestApiUrl("issues", id + ".json"));
    IssueWrapper wrapper = getHttpClient().execute(method, new GsonSingleObjectDeserializer<IssueWrapper>(GSON, IssueWrapper.class));
    if (wrapper == null) {
      return null;
    }
    return new RedmineTask(wrapper.getIssue(), this);
  }

  public String getAPIKey() {
    return myAPIKey;
  }

  public void setAPIKey(String APIKey) {
    myAPIKey = APIKey;
  }

  private boolean useApiKeyAuthentication() {
    return !StringUtil.isEmptyOrSpaces(myAPIKey) && !isUseHttpAuthentication();
  }

  @Override
  public String getPresentableName() {
    String name = super.getPresentableName();
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      name += "/projects/" + myCurrentProject.getIdentifier();
    }
    return name;
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | BASIC_HTTP_AUTHORIZATION;
  }

  @Nullable
  public RedmineProject getCurrentProject() {
    return myCurrentProject;
  }

  public void setCurrentProject(@Nullable RedmineProject project) {
    myCurrentProject = project != null && project.getId() == -1 ? UNSPECIFIED_PROJECT : project;
  }
}
