package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.tasks.Task;
import com.intellij.util.xmlb.annotations.Tag;
import org.intellij.lang.xpath.XPathFileType;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;

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

  @Override
  public FileType getSelectorFileType() {
    return XPathFileType.XPATH2;
  }

  @Override
  public Task[] doParseIssues(String response) throws Exception {
    String idSelector = getSelectorPath("id"), summarySelector = getSelectorPath("summary");
    if (idSelector == null || summarySelector == null) {
      throw new IllegalArgumentException("Selectors 'task', 'id' and 'summary' are mandatory");
    }

    Document document = ourDocumentBuilder.parse(new ByteArrayInputStream(response.getBytes()));

    XPathExpression idPath = ourXPathFactory.newXPath().compile(idSelector);
    NodeList idNodes = (NodeList)idPath.evaluate(document, XPathConstants.NODESET);

    XPathExpression summaryPath = ourXPathFactory.newXPath().compile(summarySelector);
    NodeList summaryNodes = (NodeList)summaryPath.evaluate(document, XPathConstants.NODESET);

    if (summaryNodes.getLength() != idNodes.getLength()) {
      // popup will be shown to the user
      throw new IllegalArgumentException("Number of tasks selected by 'id' and 'summary' XPath expressions is not the same.");
    }

    ArrayList<Task> tasks = new ArrayList<Task>(idNodes.getLength());
    for (int i = 0; i < idNodes.getLength(); i++) {
      String id = idNodes.item(i).getNodeValue();
      String summary = summaryNodes.item(i).getNodeValue();
      tasks.add(new GenericTask(id, summary, myRepository));
    }
    return tasks.toArray(new Task[tasks.size()]);
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
