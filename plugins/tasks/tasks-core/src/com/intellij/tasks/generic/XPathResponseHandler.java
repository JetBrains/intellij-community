package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.xpath.XPathFileType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
@Tag("XPathResponseHandler")
public final class XPathResponseHandler extends SelectorBasedResponseHandler {
  private final Map<String, XPath> myCompiledCache = new HashMap<String, XPath>();

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public XPathResponseHandler() {
    // empty
  }

  public XPathResponseHandler(GenericRepository repository) {
    super(repository);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Task[] doParseIssues(String response, int max) throws Exception {
    Document document = new SAXBuilder(false).build(new StringReader(response));
    Element root = document.getRootElement();
    XPath idXPath = lazyCompile(getSelectorPath(ID));
    XPath summaryXPath = lazyCompile(getSelectorPath(SUMMARY));
    assert idXPath != null && summaryXPath != null;    // should not be empty
    XPath descriptionXPath = lazyCompile(getSelectorPath(DESCRIPTION));
    XPath updatedXPath = lazyCompile(getSelectorPath(UPDATED));
    XPath createdXPath = lazyCompile(getSelectorPath(CREATED));
    XPath issueUrlXPath = lazyCompile(getSelectorPath(ISSUE_URL));
    XPath closedXPath = lazyCompile(getSelectorPath(CLOSED));

    List<?> rawTaskElements = XPath.selectNodes(root, getSelectorPath(TASKS));
    if (!rawTaskElements.isEmpty() && !(rawTaskElements.get(0) instanceof Element)) {
      throw new Exception(String.format("Selector 'tasks' should match list of XML elements. Got '%s' instead.", rawTaskElements.toString()));
    }
    List<Element> taskElements = (List<Element>)rawTaskElements;
    ArrayList<Task> result = new ArrayList<Task>();
    for (int i = 0; i < Math.min(taskElements.size(), max); i++) {
      Element taskElement = taskElements.get(i);
      GenericTask task = new GenericTask(selectString(taskElement, idXPath), selectString(taskElement, summaryXPath), myRepository);
      if (descriptionXPath != null) {
        task.setDescription(selectString(taskElement, descriptionXPath));
      }
      if (issueUrlXPath != null) {
        task.setIssueUrl(selectString(taskElement, issueUrlXPath));
      }
      if (createdXPath != null) {
        task.setCreated(selectDate(taskElement, createdXPath));
      }
      if (updatedXPath != null) {
        task.setUpdated(selectDate(taskElement, updatedXPath));
      }
      if (closedXPath != null) {
        task.setClosed(selectBoolean(taskElement, closedXPath));
      }
      result.add(task);
    }
    return result.toArray(new Task[result.size()]);
  }

  @Nullable
  @Override
  public Task doParseIssue(String response) throws Exception {
    Element taskElement = new SAXBuilder(false).build(response).getRootElement();
    XPath idXPath = lazyCompile(getSelectorPath(SINGLE_TASK_ID));
    XPath summaryXPath = lazyCompile(getSelectorPath(SINGLE_TASK_SUMMARY));
    assert idXPath != null && summaryXPath != null;
    XPath descriptionXPath = lazyCompile(getSelectorPath(SINGLE_TASK_DESCRIPTION));
    XPath updatedXPath = lazyCompile(getSelectorPath(SINGLE_TASK_UPDATED));
    XPath createdXPath = lazyCompile(getSelectorPath(SINGLE_TASK_CREATED));
    XPath issueUrlXPath = lazyCompile(getSelectorPath(SINGLE_TASK_ISSUE_URL));
    XPath closedXPath = lazyCompile(getSelectorPath(SINGLE_TASK_CLOSED));
    GenericTask task = new GenericTask(selectString(taskElement, idXPath),
                                       selectString(taskElement, summaryXPath),
                                       myRepository);
    if (descriptionXPath != null) {
      task.setDescription(selectString(taskElement, descriptionXPath));
    }
    if (issueUrlXPath != null) {
      task.setIssueUrl(selectString(taskElement, issueUrlXPath));
    }
    if (createdXPath != null) {
      task.setCreated(selectDate(taskElement, createdXPath));
    }
    if (updatedXPath != null) {
      task.setUpdated(selectDate(taskElement, updatedXPath));
    }
    if (closedXPath != null) {
      task.setClosed(selectBoolean(taskElement, closedXPath));
    }
    return task;
  }

  @NotNull
  private static String selectString(@NotNull Element context, @NotNull XPath xPath) throws Exception {
    String s = xPath.valueOf(context);
    if (s == null) {
      throw new Exception(String.format("XPath expression '%s' doesn't match", xPath.getXPath()));
    }
    return s;
  }

  private static long selectLong(@NotNull Element context, @NotNull XPath xPath) throws Exception {
    String s = selectString(context, xPath);
    try {
      return Long.parseLong(s);
    }
    catch (NumberFormatException e) {
      throw new Exception(
        String.format("XPath expression '%s' should match long value. Got '%s' instead", xPath.getXPath(), s));
    }
  }

  private static boolean selectBoolean(@NotNull Element context, @NotNull XPath xPath) throws Exception {
    String s = selectString(context, xPath).trim().toLowerCase();
    if (s.equals("true")) {
      return true;
    }
    else if (s.equals("false")) {
      return false;
    }
    throw new Exception(
      String.format("XPath expression '%s' should match boolean value. Got '%s' instead", xPath.getXPath(), s));
  }

  @Nullable
  private static Date selectDate(@NotNull Element context, @NotNull XPath xPath) throws Exception {
    String s = selectString(context, xPath);
    return GenericRepositoryUtil.parseISO8601Date(s);
  }

  @Nullable
  private XPath lazyCompile(@Nullable String path) throws Exception {
    if (StringUtil.isEmpty(path)) {
      return null;
    }
    if (!myCompiledCache.containsKey(path)) {
      try {
        final XPath compiled =  XPath.newInstance(path);
        myCompiledCache.put(path, compiled);
      }
      catch (JDOMException e) {
        throw new Exception(String.format("Malformed XPath expression '%s'", path));
      }
    }
    return myCompiledCache.get(path);
  }

  @Override
  public FileType getSelectorFileType() {
    return XPathFileType.XPATH;
  }

  @Override
  public ResponseType getResponseType() {
    return ResponseType.XML;
  }
}
