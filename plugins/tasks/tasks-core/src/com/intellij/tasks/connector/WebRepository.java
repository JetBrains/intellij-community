package com.intellij.tasks.connector;

import com.intellij.openapi.util.Comparing;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksIcons;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
@Tag("Web")
public class WebRepository extends BaseRepositoryImpl {
  private String myTasksListURL;
  private String myTaskPattern;
  private String myLoginURL;

  final static String SERVER_URL_PLACEHOLDER = "{serverUrl}";
  final static String USERNAME_PLACEHOLDER = "{username}";
  final static String PASSWORD_PLACEHOLDER = "{password}";
  final static String ID_PLACEHOLDER = "{id}";
  final static String SUMMARY_PLACEHOLDER = "{summary}";
  final static String DESCRIPTION_PLACEHOLDER = "{description}";
  final static String PAGE_PLACEHOLDER = "{page}";

  //final Map<String, String> myPlaceholder2Value = new HashMap<String, String>();

  @SuppressWarnings({"UnusedDeclaration"})
  public WebRepository() {
  }

  public WebRepository(final TaskRepositoryType type) {
    super(type);
  }

  public WebRepository(final WebRepository other) {
    super(other);
    myTasksListURL = other.getTasksListURL();
    myTaskPattern = other.getTaskPattern();
    myLoginURL = other.getLoginURL();
  }

  @Override
  public Task[] getIssues(@Nullable final String query, final int max, final long since) throws Exception {
    final HttpClient httpClient = getHttpClient();
    login(httpClient);

    final GetMethod getMethod = new GetMethod(getFullTasksUrl());
    httpClient.executeMethod(getMethod);
    final String response = getMethod.getResponseBodyAsString(Integer.MAX_VALUE);

    final String taskPatternWithoutPlaceholders = myTaskPattern.replaceAll("\\{.+?\\}", "");
    Matcher matcher = Pattern
      .compile(taskPatternWithoutPlaceholders,
               Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ)
      .matcher(response);

    final List<String> placeholders = getPlaceholders(myTaskPattern);
    if (matcher.groupCount() != 2 || !placeholders.contains(ID_PLACEHOLDER) || !placeholders.contains(SUMMARY_PLACEHOLDER)) {
      throw new Exception("Incorrect Task Pattern");
    }

    List<Task> tasks = new ArrayList<Task>();
    while (matcher.find()) {
      final String id = matcher.group(placeholders.indexOf(ID_PLACEHOLDER) + 1);
      final String description = matcher.group(placeholders.indexOf(SUMMARY_PLACEHOLDER) + 1);
      tasks.add(new Task() {
        @NotNull
        @Override
        public String getId() {
          return id;
        }

        @NotNull
        @Override
        public String getSummary() {
          return description;
        }

        @Nullable
        @Override
        public String getDescription() {
          return null;
        }

        @NotNull
        @Override
        public Comment[] getComments() {
          return new Comment[0];
        }

        @Nullable
        @Override
        public Icon getIcon() {
          return TasksIcons.Other;
        }

        @NotNull
        @Override
        public TaskType getType() {
          return TaskType.OTHER;
        }

        @Nullable
        @Override
        public Date getUpdated() {
          return null;
        }

        @Nullable
        @Override
        public Date getCreated() {
          return null;
        }

        @Override
        public boolean isClosed() {
          return false;
        }

        @Override
        public boolean isIssue() {
          return true;
        }

        @Nullable
        @Override
        public String getIssueUrl() {
          return null;
        }
      });
    }


    return tasks.toArray(new Task[tasks.size()]);
  }

  private void login(final HttpClient httpClient) throws IOException {
    final GetMethod method = new GetMethod(getFullLoginUrl());
    httpClient.executeMethod(method);
  }

  private static List<String> getPlaceholders(String value) {
    if (value == null) {
      return ContainerUtil.emptyList();
    }

    List<String> vars = new ArrayList<String>();
    Matcher m = Pattern.compile("\\{(.+?)\\}").matcher(value);
    while (m.find()) {
      vars.add(m.group(0));
    }
    return vars;
  }

  private String getFullTasksUrl() {
    return getTasksListURL()
      .replaceAll(placeholder2regexp(SERVER_URL_PLACEHOLDER), getUrl());
  }

  private String getFullLoginUrl() {
    return getLoginURL()
      .replaceAll(placeholder2regexp(SERVER_URL_PLACEHOLDER), getUrl())
      .replaceAll(placeholder2regexp(USERNAME_PLACEHOLDER), getUsername())
      .replaceAll(placeholder2regexp(PASSWORD_PLACEHOLDER), getPassword());
  }

  private static String placeholder2regexp(String placeholder) {
    return placeholder.replaceAll("\\{", "\\\\{");
  }

  @Nullable
  @Override
  public Task findTask(final String id) throws Exception {
    return null;
  }

  @Override
  public BaseRepository clone() {
    return new WebRepository(this);
  }

  public String getTasksListURL() {
    return myTasksListURL;
  }

  public void setTasksListURL(final String tasksListURL) {
    myTasksListURL = tasksListURL;
  }

  public String getTaskPattern() {
    return myTaskPattern;
  }

  public void setTaskPattern(final String taskPattern) {
    myTaskPattern = taskPattern;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WebRepository)) return false;
    if (!super.equals(o)) return false;
    WebRepository that = (WebRepository)o;
    if (!Comparing.equal(getTasksListURL(), that.getTasksListURL())) return false;
    if (!Comparing.equal(getTaskPattern(), that.getTaskPattern())) return false;
    if (!Comparing.equal(getLoginURL(), that.getLoginURL())) return false;
    return true;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        final Task[] issues = getIssues("", 0, 0);
        if (issues.length == 0) throw new Exception("Tasks not found. Probably, you don't login.");
      }

      @Override
      public void cancel() {
      }
    };
  }

  public String getLoginURL() {
    return myLoginURL;
  }

  public void setLoginURL(final String loginURL) {
    myLoginURL = loginURL;
  }
}
