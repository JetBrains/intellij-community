/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.youtrack;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.axis.utils.XMLChar;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
@Tag("YouTrack")
public class YouTrackRepository extends BaseRepositoryImpl {

  private String myDefaultSearch = "Assignee: me sort by: updated #Unresolved";

  /**
   * for serialization
   */
  @SuppressWarnings({"UnusedDeclaration"})
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

  public Task[] getIssues(@Nullable String request, int max, long since) throws Exception {

    String query = getDefaultSearch();
    if (StringUtil.isNotEmpty(request)) {
      query += " " + request;
    }
    String requestUrl = "/rest/project/issues/?filter=" + encodeUrl(query) + "&max=" + max + "&updatedAfter" + since;
    HttpMethod method = doREST(requestUrl, false);
    try {
      InputStream stream = method.getResponseBodyAsStream();

      // todo workaround for http://youtrack.jetbrains.net/issue/JT-7984
      String s = StreamUtil.readText(stream, CharsetToolkit.UTF8_CHARSET);
      for (int i = 0; i < s.length(); i++) {
        if (!XMLChar.isValid(s.charAt(i))) {
          s = s.replace(s.charAt(i), ' ');
        }
      }

      Element element;
      try {
        //InputSource source = new InputSource(stream);
        //source.setEncoding("UTF-8");
        //element = new SAXBuilder(false).build(source).getRootElement();
        element = new SAXBuilder(false).build(new StringReader(s)).getRootElement();
      }
      catch (JDOMException e) {
        LOG.error("Can't parse YouTrack response for " + requestUrl, e);
        throw e;
      }
      if ("error".equals(element.getName())) {
        throw new Exception("Error from YouTrack for " + requestUrl + ": '" + element.getText() + "'");
      }

      List<Element> children = element.getChildren("issue");

      final List<Task> tasks = ContainerUtil.mapNotNull(children, (NullableFunction<Element, Task>)o -> createIssue(o));
      return tasks.toArray(Task.EMPTY_ARRAY);
    }
    finally {
      method.releaseConnection();
    }
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    PostMethod method = new PostMethod(getUrl() + "/rest/user/login");
    return new HttpTestConnection<PostMethod>(method) {
      @Override
      protected void doTest(PostMethod method) throws Exception {
        login(method);
      }
    };
  }

  private HttpClient login(PostMethod method) throws Exception {
    HttpClient client = getHttpClient();
    client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(getUsername(), getPassword()));
    configureHttpMethod(method);
    method.addParameter("login", getUsername());
    method.addParameter("password", getPassword());
    client.getParams().setContentCharset("UTF-8");
    client.executeMethod(method);
    String response;
    try {
      if (method.getStatusCode() != 200) {
        throw new HttpRequests.HttpStatusException("Cannot login", method.getStatusCode(), method.getPath());
      }
      response = method.getResponseBodyAsString(1000);
    }
    finally {
      method.releaseConnection();
    }
    if (response == null) {
      throw new NullPointerException();
    }
    if (!response.contains("<login>ok</login>")) {
      int pos = response.indexOf("</error>");
      int length = "<error>".length();
      if (pos > length) {
        response = response.substring(length, pos);
      }
      throw new Exception("Cannot login: " + response);
    }
    return client;
  }

  @Nullable
  public Task findTask(@NotNull String id) throws Exception {
    final Element element = fetchRequestAsElement(id);
    return element.getName().equals("issue") ? createIssue(element) : null;
  }

  @TestOnly
  @NotNull
  public Element fetchRequestAsElement(@NotNull String id) throws Exception {
    final HttpMethod method = doREST("/rest/issue/byid/" + id, false);
    try {
      final InputStream stream = method.getResponseBodyAsStream();
      return new SAXBuilder(false).build(stream).getRootElement();
    }
    finally {
      method.releaseConnection();
    }
  }


  HttpMethod doREST(String request, boolean post) throws Exception {
    HttpClient client = login(new PostMethod(getUrl() + "/rest/user/login"));
    String uri = getUrl() + request;
    HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    configureHttpMethod(method);
    int status = client.executeMethod(method);
    if (status == 400) {
      InputStream string = method.getResponseBodyAsStream();
      Element element = new SAXBuilder(false).build(string).getRootElement();
      TaskUtil.prettyFormatXmlToLog(LOG, element);
      if ("error".equals(element.getName())) {
        throw new Exception(element.getText());
      }
    }
    return method;
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    doREST("/rest/issue/execute/" + task.getId() + "?command=" + encodeUrl("state " + state.getId()), true).releaseConnection();
  }

  @NotNull
  @Override
  public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    final HttpMethod method = doREST("/rest/issue/" + task.getId() + "/execute/intellisense?command=" + encodeUrl("state: "), false);
    try {
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      return ContainerUtil.map2Set(element.getChild("suggest").getChildren("item"), element1 -> {
        final String stateName = element1.getChildText("option");
        return new CustomTaskState(stateName, stateName);
      });
    }
    finally {
      method.releaseConnection();
    }
  }

  @Nullable
  private Task createIssue(Element element) {
    final String id = element.getAttributeValue("id");
    if (id == null) return null;
    final String summary = element.getAttributeValue("summary");
    if (summary == null) return null;
    final String description = element.getAttributeValue("description");

    String type = element.getAttributeValue("type");
    TaskType taskType = TaskType.OTHER;
    if (type != null) {
      try {
        taskType = TaskType.valueOf(type.toUpperCase());
      }
      catch (IllegalArgumentException e) {
        // do nothing
      }
    }
    final TaskType finalTaskType = taskType;

    final Date updated = new Date(Long.parseLong(element.getAttributeValue("updated")));
    final Date created = new Date(Long.parseLong(element.getAttributeValue("created")));
    final boolean resolved = element.getAttribute("resolved") != null;

    return new Task() {
      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public String getIssueUrl() {
        return getUrl() + "/issue/" + getId();
      }

      @NotNull
      @Override
      public String getId() {
        return id;
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
        return Comment.EMPTY_ARRAY;
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return LocalTaskImpl.getIconFromType(getType(), isIssue());
      }

      @NotNull
      @Override
      public TaskType getType() {
        return finalTaskType;
      }

      @Nullable
      @Override
      public Date getUpdated() {
        return updated;
      }

      @Nullable
      @Override
      public Date getCreated() {
        return created;
      }

      @Override
      public boolean isClosed() {
        // IDEA-118605
        return resolved;
      }

      @Override
      public TaskRepository getRepository() {
        return YouTrackRepository.this;
      }
    };
  }

  public String getDefaultSearch() {
    return myDefaultSearch;
  }

  public void setDefaultSearch(String defaultSearch) {
    if (defaultSearch != null) {
      myDefaultSearch = defaultSearch;
    }
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    YouTrackRepository repository = (YouTrackRepository)o;
    return Comparing.equal(repository.getDefaultSearch(), getDefaultSearch());
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.youtrack.YouTrackRepository");

  @Override
  public void updateTimeSpent(@NotNull LocalTask task, @NotNull String timeSpent, @NotNull String comment) throws Exception {
    checkVersion();
    String command = encodeUrl(String.format("work Today %s %s", timeSpent, comment));
    final HttpMethod method = doREST("/rest/issue/execute/" + task.getId() + "?command=" + command, true);
    try {
      if (method.getStatusCode() != 200) {
        InputStream stream = method.getResponseBodyAsStream();
        String message = new SAXBuilder(false).build(stream).getRootElement().getText();
        throw new Exception(message);
      }
    }
    finally {
      method.releaseConnection();
    }
  }

  private void checkVersion() throws Exception {
    HttpMethod method = doREST("/rest/workflow/version", false);
    try {
      InputStream stream = method.getResponseBodyAsStream();
      Element element = new SAXBuilder(false).build(stream).getRootElement();
      final boolean timeTrackingAvailable = element.getName().equals("version") && VersionComparatorUtil.compare(element.getChildText("version"), "4.1") >= 0;
      if (!timeTrackingAvailable) {
        throw new Exception("Time tracking is not supported in this version of Youtrack");
      }
    }
    finally {
      method.releaseConnection();
    }
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() | TIME_MANAGEMENT | STATE_UPDATING;
  }

  @TestOnly
  @Override
  public HttpClient getHttpClient() {
    return super.getHttpClient();
  }

  @NotNull
  @Override
  protected String getDefaultScheme() {
    return "https";
  }
}
