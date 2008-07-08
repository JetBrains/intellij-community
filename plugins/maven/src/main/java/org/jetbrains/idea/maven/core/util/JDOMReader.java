package org.jetbrains.idea.maven.core.util;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class JDOMReader {
  private Element myRootElement;
  private Namespace myNamespace;

  public JDOMReader(InputStream s) throws IOException {
    try {
      Document document = new SAXBuilder().build(s);
      if (!document.hasRootElement()) {
        throw new IOException("root element not found");
      }
      myRootElement = document.getRootElement();
      myNamespace = myRootElement.getNamespace();
    }
    catch (JDOMException e) {
      IOException ioException = new IOException();
      ioException.initCause(e);
      throw ioException;
    }
  }

  public Element getRootElement() {
    return myRootElement;
  }

  public Element getChild(Element element, @NonNls String tag) {
    return element.getChild(tag, myNamespace);
  }

  public List<Element> getChildren(Element element, @NonNls String tag) {
    return element.getChildren(tag, myNamespace);
  }

  public String getChildText(Element element, @NonNls String tag) {
    return element.getChildText(tag, myNamespace);
  }
}
