package com.intellij.openapi.util;

import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;

/**
 * @author mike
 */
public class JDOMBuilder {
  private JDOMBuilder() {
  }

  public static Document document(Element rootElement) {
    return new Document(rootElement);
  }

  public static Element tag(String name, Content...content) {
    final Element element = new Element(name);
    for (Content c : content) {
      if (c instanceof AttrContent) {
        AttrContent attrContent = (AttrContent)c;
        element.setAttribute(attrContent.myName, attrContent.myValue);
      }
      else {
        element.addContent(c);
      }
    }

    return element;
  }

  public static Content attr(final String name, final String value) {
    return new AttrContent(name, value);
  }

  private static class AttrContent extends Content {
    private final String myName;
    private final String myValue;

    public AttrContent(final String name, final String value) {
      myName = name;
      myValue = value;
    }

    public String getValue() {
      throw new UnsupportedOperationException();
    }
  }
}
