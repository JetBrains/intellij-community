package com.intellij.util;

import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import java.io.CharArrayWriter;
import java.io.IOException;

public class XMLOutputterTest extends TestCase {
  public void testEscapesInElementBody() throws Exception {
    Element root = new Element("root");
    root.setText("\"<>&");

    assertEquals("<root>&quot;&lt;&gt;&amp;</root>", printElement(root));
  }

  public void testEscapesInAttributeValue() throws Exception {
    Element root = new Element("root");
    root.setAttribute("escapes", "\"<>&\n\r\t");

    assertEquals("<root escapes=\"&quot;&lt;&gt;&amp;&#10;&#13;&#9;\"></root>", printElement(root));
  }

  private String printElement(Element root) throws IOException {
    XMLOutputter xmlOutputter = JDOMUtil.createOutputter("\n");
    xmlOutputter.setOmitDeclaration(true);
    xmlOutputter.setOmitEncoding(true);
    xmlOutputter.setNewlines(false);
    xmlOutputter.setExpandEmptyElements(true);

    CharArrayWriter writer = new CharArrayWriter();

    xmlOutputter.output(root, writer);
    String res = new String(writer.toCharArray());
    return res;
  }
}