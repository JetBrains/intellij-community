/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.tasks.fogbugz;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksIcons;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.util.Date;
import java.util.List;

/**
 * @author mkennedy
 */
@Tag("FogBugz")
public class FogBugzRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.fogbugz.FogBugzRepository");

  private String myToken;

  public FogBugzRepository(TaskRepositoryType type) {
    super(type);
    setUrl("https://example.fogbugz.com");
  }

  private FogBugzRepository(FogBugzRepository other) {
    super(other);
    myToken = other.myToken;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && Comparing.equal(myToken, ((FogBugzRepository)o).myToken);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public FogBugzRepository() {
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, final long since) throws Exception {
    return getCases(StringUtil.notNullize(query));
  }

  @SuppressWarnings("unchecked")
  private Task[] getCases(String q) throws Exception {
    loginIfNeeded();
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("token", myToken);
    method.addParameter("cmd", "search");
    method.addParameter("q", q);
    method.addParameter("cols", "sTitle,fOpen,dtOpened,dtLastUpdated,ixCategory");
    int status = getHttpClient().executeMethod(method);
    if (status != 200) {
      throw new Exception("Error listing cases: " + method.getStatusLine());
    }
    Document document = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getDocument();
    List<Element> errorNodes = XPath.newInstance("/response/error").selectNodes(document);
    if (!errorNodes.isEmpty()) {
      throw new Exception("Error listing cases: " + errorNodes.get(0).getText());
    }
    final XPath commentPath = XPath.newInstance("events/event");
    final List<Element> nodes = (List<Element>)XPath.newInstance("/response/cases/case").selectNodes(document);
    final List<Task> tasks = ContainerUtil.mapNotNull(nodes, new NotNullFunction<Element, Task>() {
      @NotNull
      @Override
      public Task fun(Element element) {
        return createCase(element, commentPath);
      }
    });
    return tasks.toArray(new Task[tasks.size()]);
  }

  private static TaskType getType(Element element) {
    String category = element.getChildText("ixCategory");
    if ("1".equals(category)) {
      return TaskType.BUG;
    }
    else if ("2".equals(category)) {
      return TaskType.FEATURE;
    }
    return TaskType.OTHER;
  }

  @NotNull
  private Task createCase(final Element element, final XPath commentPath) {
    final String id = element.getAttributeValue("ixBug");
    final String title = element.getChildTextTrim("sTitle");
    final TaskType type = getType(element);
    return new Task() {

      @NotNull
      @Override
      public String getId() {
        return id;
      }

      @NotNull
      @Override
      public String getSummary() {
        return title;
      }

      @Nullable
      @Override
      public String getDescription() {
        return null;
      }

      @NotNull
      @Override
      @SuppressWarnings("unchecked")
      public Comment[] getComments() {
        List<Element> nodes;
        try {
          nodes = commentPath.selectNodes(element);
        }
        catch (Exception e) {
          throw new RuntimeException("Error selecting comment nodes", e);
        }
        List<Comment> comments = ContainerUtil.mapNotNull(nodes, new NotNullFunction<Element, Comment>() {
          @NotNull
          @Override
          public Comment fun(Element element) {
            return createComment(element);
          }

          private Comment createComment(final Element element) {
            return new Comment() {
              @Override
              public String getText() {
                return element.getChildTextTrim("s");
              }

              @Nullable
              @Override
              public String getAuthor() {
                return element.getChildTextTrim("sPerson");
              }

              @Nullable
              @Override
              public Date getDate() {
                return parseDate(element.getChildTextTrim("dt"));
              }
            };
          }
        });
        return comments.toArray(new Comment[comments.size()]);
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return TasksIcons.Fogbugz;
      }

      @NotNull
      @Override
      public TaskType getType() {
        return type;
      }

      @Nullable
      @Override
      public Date getUpdated() {
        return parseDate(element.getChildText("dtLastUpdated"));
      }

      @Nullable
      @Override
      public Date getCreated() {
        return parseDate(element.getChildTextTrim("dtOpened"));
      }

      @Override
      public boolean isClosed() {
        return !Boolean.valueOf(element.getChildTextTrim("fOpen"));
      }

      @Override
      public boolean isIssue() {
        return true;
      }

      @Nullable
      @Override
      public String getIssueUrl() {
        return getUrl() + "/default.asp?" + getId();
      }

      @Nullable
      @Override
      public TaskRepository getRepository() {
        return FogBugzRepository.this;
      }
    };
  }

  @Nullable
  @Override
  public Task findTask(@NotNull String id) throws Exception {
    Task[] tasks = getCases(id);
    switch (tasks.length) {
      case 0:
        return null;
      case 1:
        return tasks[0];
      default:
        LOG.warn("Expected unique case for case id: " + id + ", got " + tasks.length + " instead. Using the first one.");
        return tasks[0];
    }
  }

  @NotNull
  @Override
  public BaseRepository clone() {
    return new FogBugzRepository(this);
  }

  private void loginIfNeeded() throws Exception {
    if (StringUtil.isEmpty(myToken)) {
      login(getLoginMethod());
    }
  }

  private void login(@NotNull PostMethod method) throws Exception {
    LOG.debug("Requesting new token");
    int status = getHttpClient().executeMethod(method);
    if (status != 200) {
      throw new Exception("Error logging in: " + method.getStatusLine());
    }
    Document document = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getDocument();
    XPath path = XPath.newInstance("/response/token");
    Element result = (Element)path.selectSingleNode(document);
    if (result == null) {
      Element error = (Element)XPath.newInstance("/response/error").selectSingleNode(document);
      throw new Exception(error == null ? "Error logging in" : error.getText());
    }
    myToken = result.getTextTrim();
  }

  @NotNull
  private PostMethod getLoginMethod() {
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("cmd", "logon");
    method.addParameter("email", getUsername());
    method.addParameter("password", getPassword());
    return method;
  }

  @NotNull
  private PostMethod getLogoutMethod() throws Exception {
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("cmd", "logoff");
    assert myToken != null;
    method.addParameter("token", myToken);
    return method;
  }

  @Nullable
  @Override
  public CancellableConnection createCancellableConnection() {
    return new CancellableConnection() {
      PostMethod myMethod;

      @Override
      protected void doTest() throws Exception {
        if (StringUtil.isNotEmpty(myToken)) {
          myMethod = getLogoutMethod();
          LOG.debug("Revoking previously used token");
          getHttpClient().executeMethod(myMethod);
        }
        myMethod = getLoginMethod();
        login(myMethod);
      }

      @Override
      public void cancel() {
        if (myMethod != null) {
          myMethod.abort();
        }
      }
    };
  }

  @NotNull
  private static Date parseDate(@NotNull String string) {
    try {
      return DatatypeFactory.newInstance().newXMLGregorianCalendar(string).toGregorianCalendar().getTime();
    }
    catch (DatatypeConfigurationException e) {
      throw new RuntimeException("Error configuring datatype factory", e);
    }
  }

  @Override
  public String getComment() {
    return "{id} (e.g. 2344245), {summary}";
  }

  @Tag("token")
  @NotNull
  public String getEncodedToken() {
    // The same approach as used for passwords in BaseRepository
    return PasswordUtil.encodePassword(myToken);
  }

  @SuppressWarnings("unused")
  public void setEncodedToken(@Nullable String token) {
    try {
      myToken = PasswordUtil.decodePassword(token);
    }
    catch (NumberFormatException ignored) {
    }
  }

}
