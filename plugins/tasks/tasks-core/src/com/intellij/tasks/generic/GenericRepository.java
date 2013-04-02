package com.intellij.tasks.generic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xml.util.XmlUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
@Tag("Generic")
public class GenericRepository extends BaseRepositoryImpl {
  private String myTasksListURL = "";
  private String myTaskPattern = "";
  private String myLoginURL = "";
  private String myLoginMethodType = GenericRepositoryEditor.GET;
  private String myTasksListMethodType = GenericRepositoryEditor.GET;
  private ResponseType myResponseType = ResponseType.XML;
  private List<TemplateVariable> myTemplateVariables = new ArrayList<TemplateVariable>();

  final static String SERVER_URL_PLACEHOLDER = "{serverUrl}";
  final static String USERNAME_PLACEHOLDER = "{username}";
  final static String PASSWORD_PLACEHOLDER = "{password}";
  final static String ID_PLACEHOLDER = "{id}";
  final static String SUMMARY_PLACEHOLDER = "{summary}";
  final static String QUERY_PLACEHOLDER = "{query}";
  final static String MAX_COUNT_PLACEHOLDER = "{count}";
  //todo
  final static String DESCRIPTION_PLACEHOLDER = "{description}";
  //todo
  final static String PAGE_PLACEHOLDER = "{page}";

  @SuppressWarnings({"UnusedDeclaration"})
  public GenericRepository() {
  }

  public GenericRepository(final TaskRepositoryType type) {
    super(type);
    resetToDefaults();
  }

  public GenericRepository(final GenericRepository other) {
    super(other);
    myTasksListURL = other.getTasksListURL();
    myTaskPattern = other.getTaskPattern();
    myLoginURL = other.getLoginURL();
    myLoginMethodType = other.getLoginMethodType();
    myTasksListMethodType = other.getTasksListMethodType();
    myResponseType = other.getResponseType();
    myTemplateVariables = other.getTemplateVariables();
  }

  @Override
  public boolean isConfigured() {
    return StringUtil.isNotEmpty(myTasksListURL) && StringUtil.isNotEmpty(myTaskPattern);
  }

  @Override
  public Task[] getIssues(@Nullable final String query, final int max, final long since) throws Exception {
    final HttpClient httpClient = getHttpClient();

    if (!isLoginAnonymously() && !isUseHttpAuthentication()) login(httpClient);

    final HttpMethod method = getTaskListsMethod(query != null ? query : "", max);
    httpClient.executeMethod(method);
    if (method.getStatusCode() != 200) throw new Exception("Cannot get tasks: HTTP status code " + method.getStatusCode());
    final String response = method.getResponseBodyAsString();
    return parseResponse(query, max, response);
  }

  public Task[] parseResponse(String query, int max, String response) throws Exception {

    final List<String> placeholders = getPlaceholders(getTaskPattern());
    if (!placeholders.contains(ID_PLACEHOLDER) || !placeholders.contains(SUMMARY_PLACEHOLDER)) {
      throw new Exception("Incorrect Task Pattern");
    }

    final String taskPatternWithoutPlaceholders = getTaskPattern().replaceAll("\\{.+?\\}", "");
    Matcher matcher = Pattern
      .compile(taskPatternWithoutPlaceholders,
               Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL | Pattern.UNICODE_CASE | Pattern.CANON_EQ)
      .matcher(response);

    List<Task> tasks = new ArrayList<Task>();
    while (matcher.find()) {
      String id = matcher.group(placeholders.indexOf(ID_PLACEHOLDER) + 1);
      String summary = matcher.group(placeholders.indexOf(SUMMARY_PLACEHOLDER) + 1);
      if (myResponseType == ResponseType.XML && summary != null) {
        final String finalSummary = summary;
        summary = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Override
          public String compute() {
            XmlElementFactory factory = XmlElementFactory.getInstance(ProjectManager.getInstance().getDefaultProject());
            XmlTag text = factory.createTagFromText("<a>" + finalSummary + "</a>");
            return XmlUtil.decode(text.getValue().getTrimmedText());
          }
        });
      }
      tasks.add(new GenericTask(id, summary, this));
    }

    final boolean searchSupported = getTasksListURL().contains(QUERY_PLACEHOLDER);
    if (!searchSupported) {
      tasks = TaskSearchSupport.filterTasks(query != null ? query : "", tasks);
    }

    tasks = tasks.subList(0, Math.min(max, tasks.size()));

    return tasks.toArray(new Task[tasks.size()]);
  }

  private HttpMethod getTaskListsMethod(final String query, final int max) {
    String requestUrl = getFullTasksUrl(query, max);
    final HttpMethod method =
      GenericRepositoryEditor.GET.equals(getTasksListMethodType()) ? new GetMethod(requestUrl) : getPostMethodFromURL(requestUrl);
    configureHttpMethod(method);
    return method;
  }

  @Override
  protected void configureHttpMethod(final HttpMethod method) {
    super.configureHttpMethod(method);
    method.addRequestHeader("accept", getResponseType().getMimeType());
  }

  private void login(final HttpClient httpClient) throws Exception {
    final HttpMethod method = getLoginMethod();
    httpClient.executeMethod(method);
    if (method.getStatusCode() != 200) throw new Exception("Cannot login: HTTP status code " + method.getStatusCode());
  }

  private HttpMethod getLoginMethod() {
    String requestUrl = getFullLoginUrl();
    return GenericRepositoryEditor.GET.equals(getLoginMethodType()) ? new GetMethod(requestUrl) : getPostMethodFromURL(requestUrl);
  }

  private static HttpMethod getPostMethodFromURL(final String requestUrl) {
    int n = requestUrl.indexOf('?');
    if (n == -1) {
      return new PostMethod(requestUrl);
    }

    PostMethod postMethod = new PostMethod(requestUrl.substring(0, n));
    n = requestUrl.indexOf('?');
    String[] requestParams = requestUrl.substring(n + 1).split("&");
    for (String requestParam : requestParams) {
      String[] nv = requestParam.split("=");
      if (nv.length == 1) {
        postMethod.addParameter(nv[0], "");
      } else {
        postMethod.addParameter(nv[0], nv[1]);
      }
    }
    return postMethod;
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

  private String getFullTasksUrl(final String query, final int max) {
    return replaceTemplateVariables(getTasksListURL())
      .replaceAll(Pattern.quote(SERVER_URL_PLACEHOLDER), getUrl())
      .replaceAll(Pattern.quote(QUERY_PLACEHOLDER), encodeUrl(query))
      .replaceAll(Pattern.quote(MAX_COUNT_PLACEHOLDER), String.valueOf(max));
  }

  private String getFullLoginUrl() {
    return replaceTemplateVariables(getLoginURL())
      .replaceAll(Pattern.quote(SERVER_URL_PLACEHOLDER), getUrl())
      .replaceAll(Pattern.quote(USERNAME_PLACEHOLDER), encodeUrl(getUsername()))
      .replaceAll(Pattern.quote(PASSWORD_PLACEHOLDER), encodeUrl(getPassword()));
  }

  @Nullable
  @Override
  public Task findTask(final String id) throws Exception {
    return null;
  }

  @Override
  public GenericRepository clone() {
    return new GenericRepository(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof GenericRepository)) return false;
    if (!super.equals(o)) return false;
    GenericRepository that = (GenericRepository)o;
    if (!Comparing.equal(getTasksListURL(), that.getTasksListURL())) return false;
    if (!Comparing.equal(getTaskPattern(), that.getTaskPattern())) return false;
    if (!Comparing.equal(getLoginURL(), that.getLoginURL())) return false;
    if (!Comparing.equal(getLoginMethodType(), that.getLoginMethodType())) return false;
    if (!Comparing.equal(getTasksListMethodType(), that.getTasksListMethodType())) return false;
    if (!Comparing.equal(getResponseType(), that.getResponseType())) return false;
    if (!Comparing.equal(getTemplateVariables(), that.getTemplateVariables())) return false;
    return true;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        final Task[] issues = getIssues("", 1, 0);
        if (issues.length == 0) throw new Exception("Tasks not found. Probably, you don't login.");
      }

      @Override
      public void cancel() {
      }
    };
  }

  private String replaceTemplateVariables(String s) {
    String answer = new String(s);
    for (TemplateVariable templateVariable : getTemplateVariables()) {
      answer = answer.replaceAll(Pattern.quote("{" + templateVariable.getName() + "}"), templateVariable.getValue());
    }
    return answer;
  }

  public String getLoginURL() {
    return myLoginURL;
  }

  public void setLoginURL(final String loginURL) {
    myLoginURL = loginURL;
  }

  public void setLoginMethodType(final String loginMethodType) {
    myLoginMethodType = loginMethodType;
  }

  public void setTasksListMethodType(final String tasksListMethodType) {
    myTasksListMethodType = tasksListMethodType;
  }

  public String getLoginMethodType() {
    return myLoginMethodType;
  }

  public String getTasksListMethodType() {
    return myTasksListMethodType;
  }

  public ResponseType getResponseType() {
    return myResponseType;
  }

  public void setResponseType(final ResponseType responseType) {
    myResponseType = responseType;
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

  public List<TemplateVariable> getTemplateVariables() {
    return myTemplateVariables;
  }

  public void setTemplateVariables(final List<TemplateVariable> templateVariables) {
    myTemplateVariables = templateVariables;
  }

  public void resetToDefaults() {
    myTasksListURL = getTasksListURLDefault();
    myTaskPattern = getTaskPatternDefault();
    myLoginURL = getLoginURLDefault();
    myLoginMethodType = getLoginMethodTypeDefault();
    myTasksListMethodType = getTasksListMethodTypeDefault();
    myResponseType = getResponseTypeDefault();
    myTemplateVariables = getTemplateVariablesDefault();
  }

  protected List<TemplateVariable> getTemplateVariablesDefault() {
    return new ArrayList<TemplateVariable>();
  }

  protected ResponseType getResponseTypeDefault() {
    return ResponseType.XML;
  }

  protected String getTasksListMethodTypeDefault() {
    return GenericRepositoryEditor.GET;
  }

  protected String getLoginMethodTypeDefault() {
    return GenericRepositoryEditor.GET;
  }

  protected String getLoginURLDefault() {
    return "";
  }

  protected String getTaskPatternDefault() {
    return "";
  }

  protected String getTasksListURLDefault() {
    return "";
  }

  @Override
  protected int getFeatures() {
    return LOGIN_ANONYMOUSLY | BASIC_HTTP_AUTHORIZATION;
  }
}
