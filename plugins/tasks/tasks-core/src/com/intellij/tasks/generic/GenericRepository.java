// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepositorySubtype;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.tasks.generic.GenericRepositoryUtil.concat;
import static com.intellij.tasks.generic.GenericRepositoryUtil.substituteTemplateVariables;
import static com.intellij.tasks.generic.TemplateVariable.FactoryVariable;

/**
 * @author Evgeny.Zakrevsky
 */
@Tag("Generic")
public final class GenericRepository extends BaseRepositoryImpl {
  public static final @NonNls String SERVER_URL = "serverUrl";
  public static final @NonNls String USERNAME = "username";
  public static final @NonNls String PASSWORD = "password";

  private final FactoryVariable myServerTemplateVariable = new FactoryVariable(SERVER_URL) {
    @Override
    public @NotNull String getValue() {
      return GenericRepository.this.getUrl();
    }
  };
  private final FactoryVariable myUserNameTemplateVariable = new FactoryVariable(USERNAME) {
    @Override
    public @NotNull String getValue() {
      return GenericRepository.this.getUsername();
    }
  };
  private final FactoryVariable myPasswordTemplateVariable = new FactoryVariable(PASSWORD, true) {
    @Override
    public @NotNull String getValue() {
      return GenericRepository.this.getPassword();
    }
  };

  private final List<FactoryVariable> myPredefinedTemplateVariables = Arrays.asList(myServerTemplateVariable,
                                                                                    myUserNameTemplateVariable,
                                                                                    myPasswordTemplateVariable);
  private String myLoginURL = "";
  private String myTasksListUrl = "";
  private String mySingleTaskUrl;

  private HTTPMethod myLoginMethodType = HTTPMethod.GET;
  private HTTPMethod myTasksListMethodType = HTTPMethod.GET;
  private HTTPMethod mySingleTaskMethodType = HTTPMethod.GET;

  private ResponseType myResponseType = ResponseType.JSON;

  private EnumMap<ResponseType, ResponseHandler> responseHandlerMap = new EnumMap<>(ResponseType.class);

  private List<TemplateVariable> myTemplateVariables = new ArrayList<>();

  private String mySubtypeName;
  private boolean myDownloadTasksInSeparateRequests;

  /**
   * Serialization constructor
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public GenericRepository() {
    resetToDefaults();
  }

  public GenericRepository(final TaskRepositoryType type) {
    super(type);
    resetToDefaults();
  }

  /**
   * Cloning constructor
   */
  public GenericRepository(final GenericRepository other) {
    super(other);
    myLoginURL = other.getLoginUrl();
    myTasksListUrl = other.getTasksListUrl();
    mySingleTaskUrl = other.getSingleTaskUrl();

    myLoginMethodType = other.getLoginMethodType();
    myTasksListMethodType = other.getTasksListMethodType();
    mySingleTaskMethodType = other.getSingleTaskMethodType();

    myResponseType = other.getResponseType();
    myTemplateVariables = other.getTemplateVariables();
    mySubtypeName = other.getSubtypeName();
    myDownloadTasksInSeparateRequests = other.getDownloadTasksInSeparateRequests();
    responseHandlerMap = new EnumMap<>(ResponseType.class);
    for (Map.Entry<ResponseType, ResponseHandler> e : other.responseHandlerMap.entrySet()) {
      ResponseHandler handler = e.getValue().clone();
      handler.setRepository(this);
      responseHandlerMap.put(e.getKey(), handler);
    }
  }

  public void resetToDefaults() {
    myLoginURL = "";
    myTasksListUrl = "";
    mySingleTaskUrl = "";
    myDownloadTasksInSeparateRequests = false;
    myLoginMethodType = HTTPMethod.GET;
    myTasksListMethodType = HTTPMethod.GET;
    mySingleTaskMethodType = HTTPMethod.GET;
    myResponseType = ResponseType.JSON;
    myTemplateVariables = new ArrayList<>();
    responseHandlerMap = new EnumMap<>(ResponseType.class);
    responseHandlerMap.put(ResponseType.XML, getXmlResponseHandlerDefault());
    responseHandlerMap.put(ResponseType.JSON, getJsonResponseHandlerDefault());
    responseHandlerMap.put(ResponseType.TEXT, getTextResponseHandlerDefault());
  }

  @Override
  public @NotNull GenericRepository clone() {
    return new GenericRepository(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof GenericRepository that)) return false;
    if (!super.equals(o)) return false;
    if (!Objects.equals(getLoginUrl(), that.getLoginUrl())) return false;
    if (!Objects.equals(getTasksListUrl(), that.getTasksListUrl())) return false;
    if (!Objects.equals(getSingleTaskUrl(), that.getSingleTaskUrl())) return false;
    if (!Comparing.equal(getLoginMethodType(), that.getLoginMethodType())) return false;
    if (!Comparing.equal(getTasksListMethodType(), that.getTasksListMethodType())) return false;
    if (!Comparing.equal(getSingleTaskMethodType(), that.getSingleTaskMethodType())) return false;
    if (!Comparing.equal(getResponseType(), that.getResponseType())) return false;
    if (!Comparing.equal(getTemplateVariables(), that.getTemplateVariables())) return false;
    if (!Comparing.equal(getResponseHandlers(), that.getResponseHandlers())) return false;
    if (!Comparing.equal(getDownloadTasksInSeparateRequests(), that.getDownloadTasksInSeparateRequests())) return false;
    return true;
  }

  @Override
  public boolean isConfigured() {
    if (!super.isConfigured()) return false;
    for (TemplateVariable variable : getTemplateVariables()) {
      if (variable.isShownOnFirstTab() && StringUtil.isEmpty(variable.getValue())) {
        return false;
      }
    }
    return StringUtil.isNotEmpty(myTasksListUrl) && getActiveResponseHandler().isConfigured();
  }

  @Override
  public Task[] getIssues(final @Nullable String query, final int max, final long since) throws Exception {
    if (StringUtil.isEmpty(myTasksListUrl)) {
      throw new Exception(TaskBundle.message("task.list.url.configuration.parameter.is.mandatory"));
    }
    if (!isLoginAnonymously() && !isUseHttpAuthentication()) {
      executeMethod(getLoginMethod());
    }
    List<TemplateVariable> variables = concat(getAllTemplateVariables(),
                                              new TemplateVariable("max", String.valueOf(max)),
                                              new TemplateVariable("since", String.valueOf(since)));
    String requestUrl = substituteTemplateVariables(getTasksListUrl(), variables);
    String responseBody = executeMethod(getHttpMethod(requestUrl, myTasksListMethodType));
    Task[] tasks = getActiveResponseHandler().parseIssues(responseBody, max);
    if (myResponseType == ResponseType.TEXT) {
      return tasks;
    }
    if (StringUtil.isNotEmpty(mySingleTaskUrl) && myDownloadTasksInSeparateRequests) {
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = findTask(tasks[i].getId());
      }
    }
    return tasks;
  }

  private String executeMethod(HttpMethod method) throws Exception {
    String responseBody;
    getHttpClient().executeMethod(method);
    Header contentType = method.getResponseHeader("Content-Type");
    if (contentType != null && contentType.getValue().contains("charset")) {
      // ISO-8859-1 if charset wasn't specified in response
      responseBody = StringUtil.notNullize(method.getResponseBodyAsString());
    }
    else {
      InputStream stream = method.getResponseBodyAsStream();
      if (stream == null) {
        responseBody = "";
      }
      else {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
          responseBody = StreamUtil.readText(reader);
        }
      }
    }
    if (method.getStatusCode() != HttpStatus.SC_OK) {
      throw new Exception("Request failed with HTTP error: " + method.getStatusText());
    }
    return responseBody;
  }

  private HttpMethod getHttpMethod(String requestUrl, HTTPMethod type) {
    HttpMethod method = type == HTTPMethod.GET ? new GetMethod(requestUrl) : GenericRepositoryUtil.getPostMethodFromURL(requestUrl);
    configureHttpMethod(method);
    return method;
  }

  private HttpMethod getLoginMethod() throws Exception {
    String requestUrl = substituteTemplateVariables(getLoginUrl(), getAllTemplateVariables());
    return getHttpMethod(requestUrl, myLoginMethodType);
  }

  @Override
  public @Nullable Task findTask(final @NotNull String id) throws Exception {
    List<TemplateVariable> variables = concat(getAllTemplateVariables(), new TemplateVariable("id", id));
    String requestUrl = substituteTemplateVariables(getSingleTaskUrl(), variables);
    HttpMethod method = getHttpMethod(requestUrl, mySingleTaskMethodType);
    return getActiveResponseHandler().parseIssue(executeMethod(method));
  }

  @Override
  public @Nullable CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      @Override
      protected void doTest() throws Exception {
        getIssues("", 1, 0);
      }

      @Override
      public void cancel() {
      }
    };
  }

  public void setLoginUrl(final String loginUrl) {
    myLoginURL = loginUrl;
  }

  public void setTasksListUrl(final String tasksListUrl) {
    myTasksListUrl = tasksListUrl;
  }

  public void setSingleTaskUrl(String singleTaskUrl) {
    mySingleTaskUrl = singleTaskUrl;
  }

  public String getLoginUrl() {
    return myLoginURL;
  }

  public String getTasksListUrl() {
    return myTasksListUrl;
  }

  public String getSingleTaskUrl() {
    return mySingleTaskUrl;
  }

  public void setLoginMethodType(final HTTPMethod loginMethodType) {
    myLoginMethodType = loginMethodType;
  }

  public void setTasksListMethodType(final HTTPMethod tasksListMethodType) {
    myTasksListMethodType = tasksListMethodType;
  }

  public void setSingleTaskMethodType(HTTPMethod singleTaskMethodType) {
    mySingleTaskMethodType = singleTaskMethodType;
  }

  public HTTPMethod getLoginMethodType() {
    return myLoginMethodType;
  }

  public HTTPMethod getTasksListMethodType() {
    return myTasksListMethodType;
  }

  public HTTPMethod getSingleTaskMethodType() {
    return mySingleTaskMethodType;
  }

  public ResponseType getResponseType() {
    return myResponseType;
  }

  public void setResponseType(final ResponseType responseType) {
    myResponseType = responseType;
  }

  public List<TemplateVariable> getTemplateVariables() {
    return myTemplateVariables;
  }

  /**
   * Returns all template variables including both predefined and defined by user
   */
  public @Unmodifiable List<TemplateVariable> getAllTemplateVariables() {
    return ContainerUtil.concat(myPredefinedTemplateVariables, getTemplateVariables());
  }

  public void setTemplateVariables(final List<TemplateVariable> templateVariables) {
    myTemplateVariables = templateVariables;
  }

  @Override
  public Icon getIcon() {
    if (mySubtypeName == null) {
      return super.getIcon();
    }
    @SuppressWarnings("unchecked")
    List<TaskRepositorySubtype> subtypes = getRepositoryType().getAvailableSubtypes();
    for (TaskRepositorySubtype s : subtypes) {
      if (mySubtypeName.equals(s.getName())) {
        return s.getIcon();
      }
    }
    throw new AssertionError("Unknown repository subtype");
  }

  @Override
  protected int getFeatures() {
    return LOGIN_ANONYMOUSLY | BASIC_HTTP_AUTHORIZATION;
  }

  public ResponseHandler getResponseHandler(ResponseType type) {
    return responseHandlerMap.get(type);
  }

  public ResponseHandler getActiveResponseHandler() {
    return responseHandlerMap.get(myResponseType);
  }

  @XCollection(
    elementTypes = {
      XPathResponseHandler.class,
      JsonPathResponseHandler.class,
      RegExResponseHandler.class
    }
  )
  public @NotNull List<ResponseHandler> getResponseHandlers() {
    return responseHandlerMap.isEmpty() ? Collections.emptyList() : List.copyOf(responseHandlerMap.values());
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setResponseHandlers(@NotNull List<ResponseHandler> responseHandlers) {
    responseHandlerMap.clear();
    for (ResponseHandler handler : responseHandlers) {
      responseHandlerMap.put(handler.getResponseType(), handler);
    }
    // ResponseHandler#repository field is excluded from serialization to prevent
    // circular dependency, so it has to be done manually during a serialization process
    for (ResponseHandler handler : responseHandlerMap.values()) {
      handler.setRepository(this);
    }
  }

  public ResponseHandler getXmlResponseHandlerDefault() {
    return new XPathResponseHandler(this);
  }

  public ResponseHandler getJsonResponseHandlerDefault() {
    return new JsonPathResponseHandler(this);
  }

  public ResponseHandler getTextResponseHandlerDefault() {
    return new RegExResponseHandler(this);
  }

  public String getSubtypeName() {
    return mySubtypeName;
  }

  public void setSubtypeName(String subtypeName) {
    mySubtypeName = subtypeName;
  }

  public boolean getDownloadTasksInSeparateRequests() {
    return myDownloadTasksInSeparateRequests;
  }

  public void setDownloadTasksInSeparateRequests(boolean downloadTasksInSeparateRequests) {
    myDownloadTasksInSeparateRequests = downloadTasksInSeparateRequests;
  }
}
