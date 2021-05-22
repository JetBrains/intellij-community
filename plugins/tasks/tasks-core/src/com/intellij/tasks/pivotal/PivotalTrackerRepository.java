// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.pivotal;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonMultipleObjectsDeserializer;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;
import com.intellij.tasks.pivotal.model.PivotalTrackerStory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Tag("PivotalTracker")
public class PivotalTrackerRepository extends NewBaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance(PivotalTrackerRepository.class);

  private static final String API_V5_PATH = "/services/v5";
  private static final String TOKEN_HEADER = "X-TrackerToken";
  private static final Pattern TASK_ID_REGEX = Pattern.compile("(?<projectId>\\d+)-(?<storyId>\\d+)");

  private static final List<String> STANDARD_STORY_STATES = Arrays.asList("accepted",
                                                                          "delivered",
                                                                          "finished",
                                                                          "started",
                                                                          "rejected",
                                                                          "planned",
                                                                          "unstarted",
                                                                          "unscheduled");

  // @formatter:off
  private static final TypeToken<List<PivotalTrackerStory>> LIST_OF_STORIES_TYPE = new TypeToken<>() {};
  // @formatter:on

  public static final Gson ourGson = TaskGsonUtil.createDefaultBuilder().create();

  private String myProjectId;
  private String myAPIKey;

  {
    // Don't move it to PivotalTrackerRepository(PivotalTrackerRepositoryType) because in this case
    // for already existing trackers URL will be uninitialized since it was absent in configs.
    // TODO Remove this field from the editor altogether
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
  }

  @NotNull
  @Override
  public String getRestApiPathPrefix() {
    return API_V5_PATH;
  }

  @Nullable
  @Override
  protected HttpRequestInterceptor createRequestInterceptor() {
    return new HttpRequestInterceptor() {
      @Override
      public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        request.addHeader(TOKEN_HEADER, getPassword());
      }
    };
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new HttpTestConnection(new HttpGet()) {
      @Override
      protected void doTest() throws Exception {
        myCurrentRequest = createStoriesRequest("", 0, 10, false);
        super.doTest();
      }
    };
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() &&
           StringUtil.isNotEmpty(getProjectId()) &&
           StringUtil.isNotEmpty(getPassword());
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    final List<PivotalTrackerStory> stories = getHttpClient().execute(createStoriesRequest(query, offset, limit, withClosed),
                                                                      new GsonMultipleObjectsDeserializer<>(ourGson, LIST_OF_STORIES_TYPE));

    return ContainerUtil.map2Array(stories, PivotalTrackerTask.class, story -> new PivotalTrackerTask(this, story));
  }

  @NotNull
  protected HttpGet createStoriesRequest(@Nullable String query, int offset, int limit, boolean withClosed) throws URISyntaxException {
    URI endpointUrl = new URIBuilder(getRestApiUrl("projects", myProjectId, "stories"))
      .addParameter("filter", (withClosed ? "" : "state:started,unstarted,unscheduled,rejected") +
                              (StringUtil.isEmpty(query) ? "" : " \"" + query + "\""))
      .addParameter("offset", String.valueOf(offset))
      .addParameter("limit", String.valueOf(limit))
      .build();

    return new HttpGet(endpointUrl);
  }

  @Nullable
  @Override
  public Task findTask(@NotNull final String id) throws Exception {
    final Matcher matcher = TASK_ID_REGEX.matcher(id);
    if (!matcher.matches()) {
      LOG.warn("Illegal PivotalTracker ID pattern " + id);
      return null;
    }
    final String projectId = matcher.group("projectId");
    final String storyId = matcher.group("storyId");
    final PivotalTrackerStory story = getHttpClient().execute(new HttpGet(getRestApiUrl("projects", projectId, "stories", storyId)),
                                                              new GsonSingleObjectDeserializer<>(ourGson, PivotalTrackerStory.class, true));
    return story != null ? new PivotalTrackerTask(this, story) : null;
  }

  @Override
  @Nullable
  public String extractId(@NotNull final String taskName) {
    Matcher matcher = TASK_ID_REGEX.matcher(taskName);
    return matcher.matches() ? taskName : null;
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    final Matcher matcher = TASK_ID_REGEX.matcher(task.getId());
    if (!matcher.matches()) {
      LOG.warn("Illegal PivotalTracker ID pattern " + task.getId());
      return;
    }
    final String projectId = matcher.group("projectId");
    final String storyId = matcher.group("storyId");
    final HttpPut request = new HttpPut(getRestApiUrl("projects", projectId, "stories", storyId));
    String payload = ourGson.toJson(ContainerUtil.newHashMap(Pair.create("current_state", state.getId())));
    request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
    getHttpClient().execute(request);
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    return ContainerUtil.map2Set(STANDARD_STORY_STATES, name -> new CustomTaskState(name, name));
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new PivotalTrackerRepository(this);
  }

  public String getProjectId() {
    return myProjectId;
  }

  public void setProjectId(final String projectId) {
    myProjectId = projectId;
  }

  @Override
  public String getPresentableName() {
    final String name = super.getPresentableName();
    return name + (!StringUtil.isEmpty(getProjectId()) ? "/" + getProjectId() : ""); //NON-NLS
  }

  @Override
  public boolean equals(final Object o) {
    if (!super.equals(o)) return false;
    if (!(o instanceof PivotalTrackerRepository)) return false;

    final PivotalTrackerRepository that = (PivotalTrackerRepository)o;
    if (getProjectId() != null ? !getProjectId().equals(that.getProjectId()) : that.getProjectId() != null) return false;
    return true;
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
