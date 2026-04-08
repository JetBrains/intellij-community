// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.core.forgejo.model.ForgejoIssue;
import com.intellij.tasks.core.forgejo.model.ForgejoProject;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonMultipleObjectsDeserializer;
import static com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;

@Tag("Forgejo")
public class ForgejoRepository extends NewBaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance(ForgejoRepository.class);

  private static final Pattern ID_PATTERN = Pattern.compile("\\d+");
  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();

  // @formatter:off
  private static final TypeToken<List<ForgejoProject>> LIST_OF_REPOS_TYPE = new TypeToken<>() {};
  private static final TypeToken<List<ForgejoIssue>> LIST_OF_ISSUES_TYPE = new TypeToken<>() {};
  // @formatter:on

  public static final ForgejoProject UNSPECIFIED_PROJECT = createUnspecifiedProject();

  private static @NotNull ForgejoProject createUnspecifiedProject() {
    final ForgejoProject unspecified = new ForgejoProject() {
      @Override
      public String getName() {
        return TaskBundle.message("repository.unspecified.project.name");
      }
    };
    unspecified.setId(-1);
    return unspecified;
  }

  private ForgejoProject myCurrentProject;
  private List<ForgejoProject> myProjects = null;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public ForgejoRepository() {
  }

  /**
   * Normal instantiation constructor
   */
  public ForgejoRepository(TaskRepositoryType type) {
    super(type);
  }

  /**
   * Cloning constructor
   */
  public ForgejoRepository(ForgejoRepository other) {
    super(other);
    myCurrentProject = other.myCurrentProject;
    myProjects = other.myProjects;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    final ForgejoRepository repository = (ForgejoRepository)o;
    if (!Comparing.equal(myCurrentProject, repository.myCurrentProject)) return false;
    return true;
  }

  @Override
  public @NotNull ForgejoRepository clone() {
    return new ForgejoRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    final List<ForgejoIssue> issues = fetchIssues((offset / limit) + 1, limit, !withClosed);
    return ContainerUtil.map2Array(issues, ForgejoTask.class, issue -> new ForgejoTask(this, issue));
  }

  @Override
  public @Nullable Task findTask(@NotNull String id) throws Exception {
    if (myCurrentProject == null || myCurrentProject == UNSPECIFIED_PROJECT) return null;
    String fullName = myCurrentProject.getFullName();
    if (fullName == null) return null;
    try {
      ForgejoIssue issue = fetchIssue(fullName, Integer.parseInt(id));
      return issue != null ? new ForgejoTask(this, issue) : null;
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public @Nullable CancellableConnection createCancellableConnection() {
    return new HttpTestConnection(new HttpGet()) {
      @Override
      protected void test() throws Exception {
        myCurrentRequest = new HttpGet(new URIBuilder(getRestApiUrl("user", "repos"))
                                         .addParameter("limit", "1")
                                         .build());
        super.test();
      }
    };
  }

  public @NotNull List<ForgejoProject> fetchRepos() throws Exception {
    final ResponseHandler<List<ForgejoProject>> handler = new GsonMultipleObjectsDeserializer<>(GSON, LIST_OF_REPOS_TYPE);
    final String reposUrl = getRestApiUrl("user", "repos");
    final List<ForgejoProject> result = new ArrayList<>();
    int pageNum = 1;
    while (true) {
      final URIBuilder paginatedUrl = new URIBuilder(reposUrl)
        .addParameter("page", String.valueOf(pageNum))
        .addParameter("limit", "30");
      final List<ForgejoProject> page = getHttpClient().execute(new HttpGet(paginatedUrl.build()), handler);
      if (page.isEmpty()) {
        break;
      }
      result.addAll(page);
      pageNum++;
    }
    myProjects = result;
    return Collections.unmodifiableList(myProjects);
  }

  public @NotNull List<ForgejoIssue> fetchIssues(int pageNumber, int pageSize, boolean openOnly) throws Exception {
    ensureProjectsDiscovered();
    ensureFullNameDiscovered();
    final String issuesUrl = getIssuesUrl();
    if (issuesUrl == null) {
      return Collections.emptyList();
    }
    final URIBuilder uriBuilder = new URIBuilder(issuesUrl)
      .addParameter("page", String.valueOf(pageNumber))
      .addParameter("limit", String.valueOf(pageSize))
      .addParameter("type", "issues")
      .addParameter("sort", "updated")
      .addParameter("state", openOnly ? "open" : "all");
    final ResponseHandler<List<ForgejoIssue>> handler = new GsonMultipleObjectsDeserializer<>(GSON, LIST_OF_ISSUES_TYPE);
    return getHttpClient().execute(new HttpGet(uriBuilder.build()), handler);
  }

  private void ensureFullNameDiscovered() throws Exception {
    if (myCurrentProject == null || myCurrentProject == UNSPECIFIED_PROJECT || myCurrentProject.getFullName() != null) return;
    final String repositoryUrl = getRepositoryUrl();
    if (repositoryUrl == null) return;
    final HttpGet request = new HttpGet(repositoryUrl);
    final ForgejoProject project = getHttpClient().execute(request, new GsonSingleObjectDeserializer<>(GSON, ForgejoProject.class, true));
    if (project != null && project.getFullName() != null) {
      myCurrentProject.setFullName(project.getFullName());
    }
  }

  public @Nullable ForgejoIssue fetchIssue(@NotNull String repoFullName, int issueNumber) throws Exception {
    ensureProjectsDiscovered();
    final String[] parts = repoFullName.split("/", 2);
    final HttpGet request = new HttpGet(getRestApiUrl("repos", parts[0], parts[1], "issues", issueNumber));
    return getHttpClient().execute(request, new GsonSingleObjectDeserializer<>(GSON, ForgejoIssue.class, true));
  }

  private @Nullable String getRepositoryUrl() {
    if (myCurrentProject == null || myCurrentProject == UNSPECIFIED_PROJECT) return null;
    return getRestApiUrl("repositories", myCurrentProject.getId());
  }

  private @Nullable String getIssuesUrl() {
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      final String fullName = myCurrentProject.getFullName();
      if (fullName != null) {
        final String[] parts = fullName.split("/", 2);
        return getRestApiUrl("repos", parts[0], parts[1], "issues");
      }
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String getPresentableName() {
    String name = getUrl();
    if (myCurrentProject != null && myCurrentProject != UNSPECIFIED_PROJECT) {
      name += "/" + myCurrentProject.getFullName();
    }
    return name;
  }

  @Override
  public @Nullable String extractId(@NotNull String taskName) {
    return ID_PATTERN.matcher(taskName).matches() ? taskName : null;
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(getPassword());
  }

  @Override
  public @NotNull String getRestApiPathPrefix() {
    return "/api/v1/";
  }

  @Override
  protected @Nullable HttpRequestInterceptor createRequestInterceptor() {
    return (request, context) -> {
      request.addHeader("Authorization", "Bearer " + myPassword);
    };
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | TIME_MANAGEMENT;
  }

  @Override
  public void updateTimeSpent(@NotNull LocalTask task, @NotNull String timeSpent, @NotNull String comment) throws Exception {
    final Pattern issueURLPattern = Pattern.compile("https?://[^/]*/(.*)/issues/(\\d+)");
    final String issueURL = task.getIssueUrl();
    if (issueURL == null) {
      throw new IllegalArgumentException("A Forgejo-bound LocalTask should not have a null issue url.");
    }

    final Matcher issueURLMatcher = issueURLPattern.matcher(issueURL);
    if (!issueURLMatcher.matches()) {
      throw new IllegalStateException("Could not find repository path from issue URL.");
    }
    final String repoFullName = issueURLMatcher.group(1);
    final String issueNumber = issueURLMatcher.group(2);

    final Matcher timeMatcher = TIME_SPENT_PATTERN.matcher(timeSpent);
    if (!timeMatcher.matches()) {
      throw new IllegalArgumentException("Time spent must be in the format '0h 0m', got: " + timeSpent);
    }
    final int hours = Integer.parseInt(timeMatcher.group(1));
    final int minutes = Integer.parseInt(timeMatcher.group(2));
    final long totalSeconds = hours * 3600L + minutes * 60L;

    final String[] parts = repoFullName.split("/", 2);
    final String timeUrl = getRestApiUrl("repos", parts[0], parts[1], "issues", issueNumber, "times");
    final HttpPost timeRequest = new HttpPost(timeUrl);
    timeRequest.setEntity(new StringEntity("{\"time\":" + totalSeconds + "}", ContentType.APPLICATION_JSON));

    LOG.debug("Sending POST request to " + timeUrl);

    final HttpResponse timeResponse = getHttpClient().execute(timeRequest);
    if (timeResponse.getStatusLine().getStatusCode() != 200) {
      LOG.error("Failed adding time spent to Forgejo. Received error code: " + timeResponse.getStatusLine().getStatusCode());
      throw new RuntimeException("Could not add time to the remote task.");
    }

    if (!StringUtil.isEmptyOrSpaces(comment)) {
      final String commentUrl = getRestApiUrl("repos", parts[0], parts[1], "issues", issueNumber, "comments");
      final HttpPost commentRequest = new HttpPost(commentUrl);
      commentRequest.setEntity(new StringEntity("{\"body\":" + GSON.toJson(comment) + "}", ContentType.APPLICATION_JSON));

      final HttpResponse commentResponse = getHttpClient().execute(commentRequest);
      if (commentResponse.getStatusLine().getStatusCode() != 201) {
        LOG.error("Failed adding a comment to Forgejo. Received error code: " + commentResponse.getStatusLine().getStatusCode());
        throw new RuntimeException("Could not add a comment to the remote task.");
      }
    }
  }

  public void setCurrentProject(@Nullable ForgejoProject project) {
    myCurrentProject = project != null && project.getId() == -1 ? UNSPECIFIED_PROJECT : project;
  }

  public ForgejoProject getCurrentProject() {
    return myCurrentProject;
  }

  public @NotNull List<ForgejoProject> getProjects() {
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
      fetchRepos();
    }
  }

  @TestOnly
  @Transient
  public void setProjects(@NotNull List<ForgejoProject> projects) {
    myProjects = projects;
  }
}
