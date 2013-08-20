package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.xpath.XPathFileType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
@Tag("XPathResponseHandler")
public final class XPathResponseHandler extends SelectorBasedResponseHandler {

  private final static DocumentBuilder ourDocumentBuilder = createDocumentBuilder();
  private static DocumentBuilder createDocumentBuilder() {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new AssertionError("Can't create DocumentBuilder");
    }
  }

  private final static XPathFactory ourXPathFactory = createXPathFactory();
  private static XPathFactory createXPathFactory() {
    return XPathFactory.newInstance();
  }

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
  public Task[] doParseIssues(String response) throws Exception {
    Document document = new SAXBuilder(false).build(new StringReader(response));
    Element root = document.getRootElement();
    XPath idXPath = compile(getSelectorPath("id"));
    XPath summaryXPath = compile(getSelectorPath("summary"));
    assert idXPath != null && summaryXPath != null;    // should not be empty
    XPath descriptionXPath = compile(getSelectorPath("description"));
    XPath updatedXPath = compile(getSelectorPath("updated"));
    XPath createdXPath = compile(getSelectorPath("created"));
    XPath issueUrlXPath = compile(getSelectorPath("issueUrl"));
    XPath closedXPath = compile(getSelectorPath("closed"));

    List<?> rawTaskElements = XPath.selectNodes(root, getSelectorPath("tasks"));
    if (!rawTaskElements.isEmpty() && !(rawTaskElements.get(0) instanceof Element)) {
      throw new Exception(String.format("Selector 'tasks' should match list of elements. Got '%s' instead.", rawTaskElements.toString()));
    }
    List<Element> taskElements = (List<Element>)rawTaskElements;
    ArrayList<Task> result = new ArrayList<Task>();
    for (Element taskElement : taskElements) {
      GenericTask task = new GenericTask(selectId(taskElement, idXPath), selectString(taskElement, summaryXPath, "summary"), myRepository);
      task.setDescription(selectString(taskElement, descriptionXPath, "description"));
      task.setIssueUrl(selectString(taskElement, issueUrlXPath, "issueUrl"));
      task.setCreated(selectDate(taskElement, createdXPath, "created"));
      task.setUpdated(selectDate(taskElement, updatedXPath, "updated"));
      Boolean selected = selectBoolean(taskElement, closedXPath, "closed");
      if (selected != null) {
        task.setClosed(selected);
      }
      result.add(task);
    }
    return result.toArray(new Task[result.size()]);
  }

  @NotNull
  private String selectId(@NotNull Element context, @NotNull XPath idXPath) throws Exception {
    Object value = idXPath.selectSingleNode(context);
    if (!(value instanceof String) && !(value instanceof Long)) {
      throw new Exception("Selector 'id' should match either String or Long value");
    }
    return String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private <T> T selectSingleNodeAndCheckType(@NotNull Element context,
                                             @Nullable XPath xPath,
                                             @NotNull String selectorName,
                                             @NotNull Class<T> cls) throws Exception {
    if (xPath == null) {
      return null;
    }
    Object value = xPath.selectSingleNode(context);
    if (!cls.isInstance(value)) {
      throw new Exception(
        String.format("Selector %s should match '%s'. Got '%s' instead", selectorName, cls.getSimpleName(), value.toString()));
    }
    if (value == null) {
      throw new Exception(String.format("Selector '%s' doesn't match", selectorName));
    }
    return (T)value;
  }

  //@Nullable
  //private String selectString(@NotNull Element context, @Nullable XPath xPath, @NotNull String selectorName) throws Exception {
  //  Object selected = xPath.selectSingleNode(context);
  //  if (selected == null) {
  //    return null;
  //  }
  //  if (selected instanceof Attribute) {
  //    return ((Attribute)selected).getValue();
  //  }
  //  else if (selected instanceof Text) {
  //    return ((Text)selected).getText();
  //  }
  //  else if (selected instanceof Content) {
  //    // element, CDATA, Comment or ProcessingInstruction
  //    throw new Exception(
  //      String.format("Selector '%s' should match single atomic value. Got '%s' instead.", selectorName, selected.toString()));
  //  }
  //  // String, Double, Boolean
  //  return selected.toString();
  //}

  @Nullable
  private String selectString(@NotNull Element context, @Nullable XPath xPath, @NotNull String selectorName) throws Exception {
    return selectSingleNodeAndCheckType(context, xPath, selectorName, String.class);
  }

  @Nullable
  private Boolean selectBoolean(@NotNull Element context, @Nullable XPath xPath, @NotNull String selectorName) throws Exception {
    return selectSingleNodeAndCheckType(context, xPath, selectorName, Boolean.class);
  }

  @Nullable
  private Date selectDate(@NotNull Element context, @Nullable XPath xPath, @NotNull String selectorName) throws Exception {
    String s = selectString(context, xPath, selectorName);
    return s == null ? null : GenericRepositoryUtil.parseISO8601Date(s);
  }



  @Nullable
  private XPath compile(@Nullable String path) throws Exception {
    if (StringUtil.isEmpty(path)) {
      return null;
    }
    try {
      return XPath.newInstance(path);
    }
    catch (JDOMException e) {
      throw new Exception(String.format("Malformed XPath expression '%s'", path));
    }
  }

  @Override
  public FileType getSelectorFileType() {
    return XPathFileType.XPATH2;
  }

  @Nullable
  @Override
  public Task doParseIssue(String response) throws Exception {
    return null;
  }

  @Override
  public ResponseType getResponseType() {
    return ResponseType.XML;
  }
}
