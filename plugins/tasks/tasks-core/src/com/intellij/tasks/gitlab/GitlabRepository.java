package com.intellij.tasks.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import com.intellij.tasks.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.*;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.intellij.tasks.httpclient.ResponseUtil.GsonMultipleObjectsDeserializer;
import static com.intellij.tasks.httpclient.ResponseUtil.GsonSingleObjectDeserializer;

/**
 * @author Mikhail Golubev
 */
@Tag("Gitlab")
public class GitlabRepository extends NewBaseRepositoryImpl {

  @NonNls public static final String REST_API_PATH_PREFIX = "/api/v3/";
  public static final Gson GSON = TaskUtil.installDateDeserializer(new GsonBuilder()).create();
  public static final TypeToken<List<GitlabProject>> LIST_OF_PROJECTS_TYPE = new TypeToken<List<GitlabProject>>() {};
  public static final TypeToken<List<GitlabIssue>> LIST_OF_ISSUES_TYPE = new TypeToken<List<GitlabIssue>>() {};
  public static final GitlabProject UNSPECIFIED_PROJECT = new GitlabProject() {
    @Override
    public String getName() {
      return "-- from all projects --";
    }

    @Override
    public int getId() {
      return -1;
    }
  };


  private GitlabProject myCurrentProject;

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
    GitlabRepository repository = (GitlabRepository)o;
    if (!Comparing.equal(myCurrentProject, repository.myCurrentProject)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myCurrentProject != null ? myCurrentProject.hashCode() : 0;
  }

  @Override
  public GitlabRepository clone() {
    return new GitlabRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, long since) throws Exception {
    return ContainerUtil.map2Array(fetchIssues(), GitlabTask.class, new Function<GitlabIssue, GitlabTask>() {
      @Override
      public GitlabTask fun(GitlabIssue issue) {
        return new GitlabTask(GitlabRepository.this, myCurrentProject, issue);
      }
    });
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
    return new GitlabTask(this, myCurrentProject, fetchIssue(Integer.parseInt(id)));
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      private HttpGet myRequest = new HttpGet(getIssuesUrl());

      @Override
      protected void doTest() throws Exception {
        HttpResponse response = getHttpClient().execute(myRequest);
        StatusLine statusLine = response.getStatusLine();
        if (statusLine != null && statusLine.getStatusCode() != HttpStatus.SC_OK) {
          throw new Exception(statusLine.getReasonPhrase());
        }
      }

      // TODO: find more about proper request aborting in HttpClient4.x
      @Override
      public void cancel() {
        myRequest.abort();
      }
    };
  }

  @NotNull
  List<GitlabProject> fetchProjects() throws Exception {
    HttpGet request = new HttpGet(getRestApiUrl("projects"));
    ResponseHandler<List<GitlabProject>> handler = new GsonMultipleObjectsDeserializer<GitlabProject>(GSON, LIST_OF_PROJECTS_TYPE);
    return getHttpClient().execute(request, handler);
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  GitlabProject fetchProject(int id) throws Exception {
    HttpGet request = new HttpGet(getRestApiUrl("project", id));
    return getHttpClient().execute(request, new GsonSingleObjectDeserializer<GitlabProject>(GSON, GitlabProject.class));
  }

  @NotNull
  List<GitlabIssue> fetchIssues() throws Exception {
    ResponseHandler<List<GitlabIssue>> handler = new GsonMultipleObjectsDeserializer<GitlabIssue>(GSON, LIST_OF_ISSUES_TYPE);
    return getHttpClient().execute(new HttpGet(getIssuesUrl()), handler);
  }

  private String getIssuesUrl() {
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      return getRestApiUrl("projects", myCurrentProject.getId(), "issues");
    }
    return getRestApiUrl("issues");
  }

  @NotNull
  GitlabIssue fetchIssue(int id) throws Exception {
    HttpGet request = new HttpGet(getRestApiUrl("issues", id));
    ResponseHandler<GitlabIssue> handler = new GsonSingleObjectDeserializer<GitlabIssue>(GSON, GitlabIssue.class);
    return getHttpClient().execute(request, handler);
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
        request.addHeader(new BasicHeader("PRIVATE-TOKEN", myPassword));
      }
    };
  }

  public void setCurrentProject(GitlabProject currentProject) {
    myCurrentProject = currentProject.getId() == -1 ? UNSPECIFIED_PROJECT : currentProject;
  }

  public GitlabProject getCurrentProject() {
    return myCurrentProject;
  }
}
