package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

    final Element[] elements = JDOMUtil.getElements(rootElement);
    for (Element element : elements) {
      if (element.getName().equals(COMPONENT) && Comparing.equal(element.getAttributeValue(NAME), componentName)) {
        element.removeAttribute(NAME);
        if (myPathMacroManager != null) {
          myPathMacroManager.expandPaths(element);
        }
        element.getParent().removeContent(element);
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

    Element newComponentElement = new Element(COMPONENT);
    newComponentElement.setAttribute(NAME, componentName);

    final Element[] childElements = JDOMUtil.getElements(element);
    for (Element childElement : childElements) {
      childElement.detach();
      newComponentElement.addContent(childElement);
    }

    final List attributes = element.getAttributes();
    for (Object attribute : attributes) {
      Attribute attr = (Attribute)attribute;
      newComponentElement.setAttribute(attr.getName(), attr.getValue());
    }

    final Element[] elements = JDOMUtil.getElements(rootElement);
    for (Element e : elements) {
      if (e.getName().equals(COMPONENT) && Comparing.equal(e.getAttributeValue(NAME), componentName)) {
        e.detach();
      }
    }

    rootElement.addContent(newComponentElement);
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
    final Element node = getRootElement();
    if (node == null) return;

    final Element[] elements = JDOMUtil.getElements(node);

    for (Element element : elements) {
      element.detach();
    }

    Arrays.sort(elements, new Comparator<Element>() {
      public int compare(Element e1, Element e2) {
        int r = e1.getName().toLowerCase().compareTo(e2.getName().toLowerCase());
        if (r == 0) {
          final String name1 = e1.getAttributeValue(ATTR_NAME);
          final String name2 = e2.getAttributeValue(ATTR_NAME);
          if (name1 != null && name2 != null) {
            r = name1.compareTo(name2);
          }
        }
        return r;
      }
    });


    for (Element e : elements) {
      node.addContent(e);
    }
  }
}
