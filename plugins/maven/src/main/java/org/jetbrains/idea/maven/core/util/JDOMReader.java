package org.jetbrains.idea.maven.core.util;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class JDOMReader {

  protected Element rootElement;
  private Namespace namespace;

  public void init(InputStream is) {
    try {
      Document document = new SAXBuilder().build(is);
      if (document.hasRootElement()) {
        rootElement = document.getRootElement();
        this.namespace = rootElement.getNamespace();
      }
    }
    catch (JDOMException ignore) {
    }
    catch (IOException ignore) {
    }
  }

  public Element getRootElement() {
    return rootElement;
  }

  protected Element getChild(Element element, @NonNls String tag) {
    return element == null ? null : element.getChild(tag, namespace);
  }

  @SuppressWarnings({"unchecked"})
  protected List<Element> getChildren(Element element, @NonNls String tag) {
    return (List<Element>)(element == null ? Collections.EMPTY_LIST : element.getChildren(tag, namespace));
  }

  public String getChildText(Element element, @NonNls String tag) {
    return element == null ? null : element.getChildText(tag, namespace);
  }
}
