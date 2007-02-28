package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.DOMUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import javax.xml.parsers.ParserConfigurationException;
import java.util.Arrays;
import java.util.Comparator;

abstract class XmlElementStorage implements StateStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String ATTR_NAME = "name";
  @NonNls private static final String NAME = ATTR_NAME;

  private PathMacroSubstitutor myPathMacroManager;

  protected XmlElementStorage(final PathMacroSubstitutor pathMacroManager) {
    myPathMacroManager = pathMacroManager;
  }

  @Nullable
  protected abstract Element getRootElement() throws StateStorageException;

  @Nullable
  private synchronized Element getState(final String componentName) throws StateStorageException {
    final Element rootElement = getRootElement();
    if (rootElement == null) return null;

    final Element[] elements = DOMUtil.getChildElements(rootElement);
    for (Element element : elements) {
      if (element.getNodeName().equals(COMPONENT) && element.hasAttribute(NAME) && element.getAttribute(NAME).equals(componentName)) {
        element.removeAttribute(NAME);
        if (myPathMacroManager != null) {
          myPathMacroManager.expandPaths(element);
        }
        element.getParentNode().removeChild(element);
        return element;
      }
    }

    return null;
  }

  private synchronized void setState(final String componentName, final Element element) throws StateStorageException {
    final Element rootElement = getRootElement();
    if (rootElement == null) return;

    if (myPathMacroManager != null) {
      myPathMacroManager.collapsePaths(element);
    }

    final Document document = rootElement.getOwnerDocument();
    Element newComponentElement = document.createElement(COMPONENT);
    newComponentElement.setAttribute(NAME, componentName);

    final NodeList childNodes = element.getChildNodes();
    while (childNodes.getLength() > 0) {
      newComponentElement.appendChild(document.adoptNode(childNodes.item(0)));
    }

    final NamedNodeMap attributes = element.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      Attr attr = (Attr)attributes.item(i);
      newComponentElement.setAttribute(attr.getName(), attr.getValue());
    }

    final Element[] elements = DOMUtil.getChildElements(rootElement);
    for (Element e : elements) {
      if (e.getNodeName().equals(COMPONENT) && e.hasAttribute(NAME) && e.getAttribute(NAME).equals(componentName)) {
        e.getParentNode().removeChild(e);
      }
    }

    rootElement.appendChild(newComponentElement);
  }

  public void setState(final Object component, final String componentName, final Object state) throws StateStorageException {
    try {
      setState(componentName,  DefaultStateSerializer.serializeState(state));
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
    catch (WriteExternalException e) {
      LOG.debug(e);
    }
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    final Element element = getState(componentName);
    return DefaultStateSerializer.deserializeState(element, stateClass, mergeInto);
  }

  protected void sort() throws StateStorageException {
    final Node node = getRootElement();
    if (node == null) return;

    final Node[] nodes = DOMUtil.toArray(node.getChildNodes());
    for (Node n : nodes) {
      node.removeChild(n);
    }

    Arrays.sort(nodes, new Comparator<Node>() {
      public int compare(Node n1, Node n2) {
        int r = n1.getNodeName().toLowerCase().compareTo(n2.getNodeName().toLowerCase());
        if (r == 0) {
          if (n1 instanceof Element && n2 instanceof Element) {
            Element e1 = (Element)n1;
            Element e2 = (Element)n2;

            final String name1 = e1.getAttribute(ATTR_NAME);
            final String name2 = e2.getAttribute(ATTR_NAME);
            if (name1 != null && name2 != null) {
              r = name1.compareTo(name2);
            }
          }
          else {
            r = n1.getNodeValue().compareTo(n2.getNodeValue());
          }
        }
        return r;
      }
    });

    for (Node n : nodes) {
      node.appendChild(n);
    }


  }
}
