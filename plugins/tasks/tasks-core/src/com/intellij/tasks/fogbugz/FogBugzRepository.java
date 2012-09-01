package com.intellij.tasks.fogbugz;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import icons.TasksCoreIcons;
import org.apache.commons.httpclient.HttpClient;
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
  private transient String token;

  public FogBugzRepository(TaskRepositoryType type) {
    super(type);
    setUrl("https://example.fogbugz.com");
  }

  private FogBugzRepository(FogBugzRepository other) {
    super(other);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public FogBugzRepository() {
  }

  @Override
  public Task[] getIssues(@Nullable String query, int max, final long since) throws Exception {
    return getCases();
  }

  private Task[] getCases() throws Exception {
    return getCases("");
  }

  private Task[] getCases(String q) throws Exception {
    HttpClient client = login();
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("token", token);
    method.addParameter("cmd", "search");
    method.addParameter("q", q);
    method.addParameter("cols", "sTitle,fOpen,dtOpened,dtLastUpdated");
    int status = client.executeMethod(method);
    if (status != 200) {
      throw new Exception("Error listing cases: " + method.getStatusLine());
    }
    Document document = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getDocument();
    XPath path = XPath.newInstance("/response/cases/case");
    final XPath commentPath = XPath.newInstance("events/event");
    @SuppressWarnings("unchecked") final List<Element> nodes = (List<Element>)path.selectNodes(document);
    List<Task> tasks = ContainerUtil.mapNotNull(nodes, new NotNullFunction<Element, Task>() {
      @NotNull
      @Override
      public Task fun(Element element) {
        return createCase(element, commentPath);

      }
    });
    return tasks.toArray(new Task[tasks.size()]);
  }

  @NotNull
  private Task createCase(final Element element, final XPath commentPath) {
    return new Task() {
      @NotNull
      @Override
      public String getId() {
        return element.getAttributeValue("ixBug");
      }

      @NotNull
      @Override
      public String getSummary() {
        return element.getChildTextTrim("sTitle");
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

      @Nullable
      @Override
      public Icon getIcon() {
        return TasksCoreIcons.FogBugz;
      }

      @NotNull
      @Override
      public TaskType getType() {
        return TaskType.BUG;
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
        return false;
      }

      @Nullable
      @Override
      public String getIssueUrl() {
        return getUrl() + "/default.asp?" + getId();
      }
    };
  }

  @Nullable
  @Override
  public Task findTask(String id) throws Exception {
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

  @Override
  public BaseRepository clone() {
    return new FogBugzRepository(this);
  }

  private HttpClient login() throws Exception {
    PostMethod method = new PostMethod(getUrl() + "/api.asp");
    method.addParameter("cmd", "logon");
    method.addParameter("email", getUsername());
    method.addParameter("password", getPassword());
    HttpClient client = getHttpClient();
    int status = client.executeMethod(method);
    if (status != 200) {
      throw new Exception("Error logging in: " + method.getStatusLine());
    }
    Document document = new SAXBuilder(false).build(method.getResponseBodyAsStream()).getDocument();
    XPath path = XPath.newInstance("/response/token");
    Element result = (Element)path.selectSingleNode(document);
    if (result == null) {
      throw new Exception("Error logging in");
    }
    token = result.getTextTrim();
    return client;
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

}
