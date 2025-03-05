// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.fogbugz;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksCoreIcons;
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
import java.util.Objects;

/**
 * @author mkennedy
 */
@Tag("FogBugz")
public final class FogBugzRepository extends BaseRepositoryImpl {
  private static final Logger LOG = Logger.getInstance(FogBugzRepository.class);

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
    return super.equals(o) && Objects.equals(myToken, ((FogBugzRepository)o).myToken);
  }

  @SuppressWarnings("UnusedDeclaration")
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
    final List<Task> tasks = ContainerUtil.mapNotNull(nodes, (NotNullFunction<Element, Task>)element -> createCase(element, commentPath));
    return tasks.toArray(Task.EMPTY_ARRAY);
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

  private @NotNull Task createCase(final Element element, final XPath commentPath) {
    final String id = element.getAttributeValue("ixBug");
    final String title = element.getChildTextTrim("sTitle");
    final TaskType type = getType(element);
    return new Task() {

      @Override
      public @NotNull String getId() {
        return id;
      }

      @Override
      public @NotNull String getSummary() {
        return title;
      }

      @Override
      public @Nullable String getDescription() {
        return null;
      }

      @Override
      @SuppressWarnings("unchecked")
      public Comment @NotNull [] getComments() {
        List<Element> nodes;
        try {
          nodes = commentPath.selectNodes(element);
        }
        catch (Exception e) {
          throw new RuntimeException("Error selecting comment nodes", e);
        }
        List<Comment> comments = ContainerUtil.mapNotNull(nodes, new NotNullFunction<>() {
          @Override
          public @NotNull Comment fun(Element element) {
            return createComment(element);
          }

          private static Comment createComment(final Element element) {
            return new Comment() {
              @Override
              public String getText() {
                return element.getChildTextTrim("s");
              }

              @Override
              public @Nullable String getAuthor() {
                return element.getChildTextTrim("sPerson");
              }

              @Override
              public @NotNull Date getDate() {
                return parseDate(element.getChildTextTrim("dt"));
              }
            };
          }
        });
        return comments.toArray(Comment.EMPTY_ARRAY);
      }

      @Override
      public @NotNull Icon getIcon() {
        return TasksCoreIcons.Fogbugz;
      }

      @Override
      public @NotNull TaskType getType() {
        return type;
      }

      @Override
      public @NotNull Date getUpdated() {
        return parseDate(element.getChildText("dtLastUpdated"));
      }

      @Override
      public @NotNull Date getCreated() {
        return parseDate(element.getChildTextTrim("dtOpened"));
      }

      @Override
      public boolean isClosed() {
        return !Boolean.parseBoolean(element.getChildTextTrim("fOpen"));
      }

      @Override
      public boolean isIssue() {
        return true;
      }

      @Override
      public @NotNull String getIssueUrl() {
        return getUrl() + "/default.asp?" + getId();
      }

      @Override
      public @NotNull TaskRepository getRepository() {
        return FogBugzRepository.this;
      }
    };
  }

  @Override
  public @Nullable Task findTask(@NotNull String id) throws Exception {
    Task[] tasks = getCases(id);
    return switch (tasks.length) {
      case 0 -> null;
      case 1 -> tasks[0];
      default -> {
        LOG.warn("Expected unique case for case id: " + id + ", got " + tasks.length + " instead. Using the first one.");
        yield tasks[0];
      }
    };
  }

  @Override
  public @NotNull BaseRepository clone() {
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

  private @NotNull PostMethod getLoginMethod() {
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("cmd", "logon");
    method.addParameter("email", getUsername());
    method.addParameter("password", getPassword());
    return method;
  }

  private @NotNull PostMethod getLogoutMethod() {
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("cmd", "logoff");
    assert myToken != null;
    method.addParameter("token", myToken);
    return method;
  }

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

  private static @NotNull Date parseDate(@NotNull String string) {
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
}
