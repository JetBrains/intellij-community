// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.JsonResponseHandlerBuilder;
import com.intellij.tasks.youtrack.model.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * @author Dmitry Avdeev
 */
@Tag("YouTrack")
public class YouTrackRepository extends NewBaseRepositoryImpl {
  //@formatter:off
  private static final TypeToken<List<YouTrackIssue>> LIST_OF_ISSUES_TYPE = new TypeToken<>() {};
  //@formatter:on

  private static final Gson GSON = TaskGsonUtil.createDefaultBuilder().create();
  private static final Logger LOG = Logger.getInstance(YouTrackRepository.class);

  private String myDefaultSearch = "Assignee: me sort by: updated #Unresolved";

  /**
   * for serialization
   */
  @SuppressWarnings("UnusedDeclaration")
  public YouTrackRepository() {
  }

  public YouTrackRepository(TaskRepositoryType type) {
    super(type);
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new YouTrackRepository(this);
  }

  private YouTrackRepository(YouTrackRepository other) {
    super(other);
    myDefaultSearch = other.getDefaultSearch();
  }

  @Override
  public Task[] getIssues(@Nullable String query,
                          int offset,
                          int limit,
                          boolean withClosed) throws Exception {
    List<YouTrackIssue> result = fetchIssues(query, offset, limit);
    return ContainerUtil.map2Array(result, YouTrackTask.class, issue -> new YouTrackTask(this, issue));
  }

  @NotNull
  private List<YouTrackIssue> fetchIssues(@Nullable String query, int offset, int limit) throws URISyntaxException, IOException {
    String searchQuery = getDefaultSearch() + (StringUtil.isNotEmpty(query) ? " " + query : "");
    URI endpoint = new URIBuilder(getRestApiUrl("api", "issues"))
      .addParameter("query", searchQuery)
      .addParameter("fields", YouTrackIssue.DEFAULT_FIELDS)
      .addParameter("$skip", String.valueOf(offset))
      .addParameter("$top", String.valueOf(limit))
      .build();
    try {
      return getHttpClient().execute(new HttpGet(endpoint),
                                     JsonResponseHandlerBuilder.fromGson(GSON)
                                       .errorHandler(this::parseYouTrackError)
                                       .toMultipleObjects(LIST_OF_ISSUES_TYPE));
    }
    catch (YouTrackRequestFailedException e) {
      if ("invalid_query".equals(e.getErrorInfo().getError())) {
        LOG.debug("Ignoring invalid query: " + searchQuery);
        return Collections.emptyList();
      }
      else {
        throw e;
      }
    }
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new HttpTestConnection(new HttpGet()) {
      @Override
      protected void test() throws Exception {
        URI endpoint = new URIBuilder(getRestApiUrl("api", "issues"))
          .addParameter("query", myDefaultSearch)
          .addParameter("fields", YouTrackIssue.DEFAULT_FIELDS)
          .addParameter("$top", String.valueOf(10))
          .build();
        myCurrentRequest.setURI(endpoint);
        getHttpClient().execute(myCurrentRequest,
                                JsonResponseHandlerBuilder.fromGson(GSON)
                                  .errorHandler(YouTrackRepository.this::parseYouTrackError)
                                  .toNothing());
      }
    };
  }

  @Override
  @Nullable
  public Task findTask(@NotNull String id) throws Exception {
    YouTrackIssue issue = fetchIssue(id);
    return issue != null ? new YouTrackTask(this, issue) : null;
  }

  @Nullable
  private YouTrackIssue fetchIssue(@NotNull String issueId) throws URISyntaxException, IOException {
    URI endpoint = new URIBuilder(getRestApiUrl("api", "issues", issueId))
      .addParameter("fields", YouTrackIssue.DEFAULT_FIELDS)
      .build();
    return getHttpClient().execute(new HttpGet(endpoint),
                                   JsonResponseHandlerBuilder.fromGson(GSON)
                                     .errorHandler(this::parseYouTrackError)
                                     .ignoredCode(code -> code == HttpStatus.SC_NOT_FOUND)
                                     .toSingleObject(YouTrackIssue.class));
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    HttpPost request = new HttpPost(getRestApiUrl("api", "commands"));
    request.setEntity(new StringEntity(GSON.toJson(new YouTrackSingleIssueCommand(task.getId(), "state " + state.getId())),
                                       ContentType.APPLICATION_JSON));
    getHttpClient().execute(request,
                            JsonResponseHandlerBuilder.fromGson(GSON)
                              .errorHandler(this::parseYouTrackError)
                              .toNothing());
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    return ContainerUtil.map2Set(fetchAvailableStates(task.getId()), suggestion -> new CustomTaskState(suggestion, suggestion));
  }

  @NotNull
  private List<String> fetchAvailableStates(@NotNull String issueId) throws URISyntaxException, IOException {
    URI endpoint = new URIBuilder(getRestApiUrl("api", "commands", "assist"))
      .addParameter("fields", YouTrackCommandList.DEFAULT_FIELDS)
      .build();

    String setStateCommandPrefix = "state ";
    HttpPost request = new HttpPost(endpoint);
    request.setEntity(new StringEntity(GSON.toJson(new YouTrackSingleIssueCommand(issueId, setStateCommandPrefix)),
                                       ContentType.APPLICATION_JSON));
    YouTrackCommandList commandList = getHttpClient().execute(request,
                                                              JsonResponseHandlerBuilder.fromGson(GSON)
                                                                .errorHandler(this::parseYouTrackError)
                                                                .toSingleObject(YouTrackCommandList.class));
    if (commandList == null) {
      return Collections.emptyList();
    }

    return ContainerUtil.mapNotNull(commandList.getSuggestions(), suggestion -> {
      String option = suggestion.getOption();
      // YouTrack might return suggestions such as "state Fixed" or "state Won't fix".
      return option.startsWith(setStateCommandPrefix) ? null : option;
    });
  }

  @Override
  public void updateTimeSpent(@NotNull LocalTask task,
                              @NotNull String timeSpent,
                              @NotNull String comment) throws Exception {
    YouTrackPluginAdvertiserService.getInstance().showTimeTrackingNotification();
    
    Matcher matcher = TIME_SPENT_PATTERN.matcher(timeSpent);
    if (matcher.find()) {
      int hours = Integer.parseInt(matcher.group(1));
      int minutes = Integer.parseInt(matcher.group(2));
      int totalMinutes = hours * 60 + minutes;

      HttpPost request = new HttpPost(getRestApiUrl("api", "issues", task.getId(), "timeTracking", "workItems"));
      request.setEntity(new StringEntity(GSON.toJson(new YouTrackWorkItem(comment, totalMinutes)),
                                         ContentType.APPLICATION_JSON));
      getHttpClient().execute(request,
                              JsonResponseHandlerBuilder.fromGson(GSON)
                                .errorHandler(this::parseYouTrackError)
                                .toNothing());
    }
    else {
      LOG.warn("Unrecognized time pattern: " + timeSpent);
    }
  }

  @Override
  protected @Nullable HttpRequestInterceptor createRequestInterceptor() {
    return (request, context) -> request.addHeader(new BasicHeader("Accept", ContentType.APPLICATION_JSON.toString()));
  }

  public String getDefaultSearch() {
    return myDefaultSearch;
  }

  public void setDefaultSearch(String defaultSearch) {
    if (defaultSearch != null) {
      myDefaultSearch = defaultSearch;
    }
  }

  @Override
  protected @NotNull HttpClient getHttpClient() {
    return super.getHttpClient();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    YouTrackRepository repository = (YouTrackRepository)o;
    return Objects.equals(repository.getDefaultSearch(), getDefaultSearch());
  }


  @Override
  protected int getFeatures() {
    return super.getFeatures() | STATE_UPDATING | TIME_MANAGEMENT;
  }

  @NotNull
  @Override
  protected String getDefaultScheme() {
    return "https";
  }

  @Override
  public boolean isUseHttpAuthentication() {
    return true;
  }

  private static class YouTrackRequestFailedException extends RequestFailedException {
    private final YouTrackErrorInfo myErrorInfo;

    private YouTrackRequestFailedException(@NotNull YouTrackRepository repository,
                                           @NotNull YouTrackErrorInfo errorInfo) {
      super(repository, mostDescriptiveMessage(errorInfo));
      myErrorInfo = errorInfo;
    }

    @NotNull
    private YouTrackErrorInfo getErrorInfo() {
      return myErrorInfo;
    }

    @NotNull
    private static @NlsSafe String mostDescriptiveMessage(@NotNull YouTrackErrorInfo errorInfo) {
      return StringUtil.isNotEmpty(errorInfo.getErrorDescription()) ? errorInfo.getErrorDescription() :
             StringUtil.isNotEmpty(errorInfo.getError()) ? errorInfo.getError() :
             "Unknown error";
    }
  }

  @NotNull
  private RequestFailedException parseYouTrackError(@NotNull HttpResponse response) {
    try {
      return new YouTrackRequestFailedException(this, GSON.fromJson(TaskResponseUtil.getResponseContentAsReader(response),
                                                                    YouTrackErrorInfo.class));
    }
    catch (IOException e) {
      return new RequestFailedException(e);
    }
  }
}
