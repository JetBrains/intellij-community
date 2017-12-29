/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.tasks.generic;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.tasks.Task;
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

import javax.swing.*;
import java.io.InputStream;
import java.util.*;

import static com.intellij.tasks.generic.GenericRepositoryUtil.concat;
import static com.intellij.tasks.generic.GenericRepositoryUtil.substituteTemplateVariables;
import static com.intellij.tasks.generic.TemplateVariable.FactoryVariable;

/**
 * @author Evgeny.Zakrevsky
 */
@Tag("Generic")
public class GenericRepository extends BaseRepositoryImpl {

  @NonNls public static final String SERVER_URL = "serverUrl";
  @NonNls public static final String USERNAME = "username";
  @NonNls public static final String PASSWORD = "password";

  private final FactoryVariable myServerTemplateVariable = new FactoryVariable(SERVER_URL) {
    @NotNull
    @Override
    public String getValue() {
      return GenericRepository.this.getUrl();
    }
  };
  private final FactoryVariable myUserNameTemplateVariable = new FactoryVariable(USERNAME) {
    @NotNull
    @Override
    public String getValue() {
      return GenericRepository.this.getUsername();
    }
  };
  private final FactoryVariable myPasswordTemplateVariable = new FactoryVariable(PASSWORD, true) {
    @NotNull
    @Override
    public String getValue() {
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

  private ResponseType myResponseType = ResponseType.XML;

  private EnumMap<ResponseType, ResponseHandler> myResponseHandlersMap = new EnumMap<>(ResponseType.class);

  private List<TemplateVariable> myTemplateVariables = new ArrayList<>();

  private String mySubtypeName;
  private boolean myDownloadTasksInSeparateRequests;

  /**
   * Serialization constructor
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public GenericRepository() {
    // empty
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
    myResponseHandlersMap = new EnumMap<>(ResponseType.class);
    for (Map.Entry<ResponseType, ResponseHandler> e : other.myResponseHandlersMap.entrySet()) {
      ResponseHandler handler = e.getValue().clone();
      handler.setRepository(this);
      myResponseHandlersMap.put(e.getKey(), handler);
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
    myResponseType = ResponseType.XML;
    myTemplateVariables = new ArrayList<>();
    myResponseHandlersMap = new EnumMap<>(ResponseType.class);
    myResponseHandlersMap.put(ResponseType.XML, getXmlResponseHandlerDefault());
    myResponseHandlersMap.put(ResponseType.JSON, getJsonResponseHandlerDefault());
    myResponseHandlersMap.put(ResponseType.TEXT, getTextResponseHandlerDefault());
  }

  @NotNull
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
    if (!Comparing.equal(getLoginUrl(), that.getLoginUrl())) return false;
    if (!Comparing.equal(getTasksListUrl(), that.getTasksListUrl())) return false;
    if (!Comparing.equal(getSingleTaskUrl(), that.getSingleTaskUrl())) return false;
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
  public Task[] getIssues(@Nullable final String query, final int max, final long since) throws Exception {
    if (StringUtil.isEmpty(myTasksListUrl)) {
      throw new Exception("'Task list URL' configuration parameter is mandatory");
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
      responseBody = stream == null ? "" : StreamUtil.readText(stream, CharsetToolkit.UTF8_CHARSET);
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

  @Nullable
  @Override
  public Task findTask(@NotNull final String id) throws Exception {
    List<TemplateVariable> variables = concat(getAllTemplateVariables(), new TemplateVariable("id", id));
    String requestUrl = substituteTemplateVariables(getSingleTaskUrl(), variables);
    HttpMethod method = getHttpMethod(requestUrl, mySingleTaskMethodType);
    return getActiveResponseHandler().parseIssue(executeMethod(method));
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
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
  public List<TemplateVariable> getAllTemplateVariables() {
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
    return myResponseHandlersMap.get(type);
  }

  public ResponseHandler getActiveResponseHandler() {
    return myResponseHandlersMap.get(myResponseType);
  }

  @XCollection(
    elementTypes = {
      XPathResponseHandler.class,
      JsonPathResponseHandler.class,
      RegExResponseHandler.class
    }
  )
  public List<ResponseHandler> getResponseHandlers() {
    Collection<ResponseHandler> handlers = myResponseHandlersMap.values();
    return new ArrayList<>(handlers);
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setResponseHandlers(List<ResponseHandler> responseHandlers) {
    myResponseHandlersMap.clear();
    for (ResponseHandler handler : responseHandlers) {
      myResponseHandlersMap.put(handler.getResponseType(), handler);
    }
    // ResponseHandler#repository field is excluded from serialization to prevent
    // circular dependency so it has to be done manually during serialization process
    for (ResponseHandler handler : myResponseHandlersMap.values()) {
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
