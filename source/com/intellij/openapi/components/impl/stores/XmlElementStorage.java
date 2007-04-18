package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.util.*;

abstract class XmlElementStorage implements StateStorage {
  private static final Set<String> OBSOLETE_COMPONENT_NAMES = new HashSet<String>(Arrays.asList(
    "Palette"
  ));
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.XmlElementStorage");

  @NonNls private static final String COMPONENT = "component";
  @NonNls private static final String ATTR_NAME = "name";
  @NonNls private static final String NAME = ATTR_NAME;

  private PathMacroSubstitutor myPathMacroManager;
  private Set<Element> mySavedElements = new HashSet<Element>();
  private Document myDocument;

  protected XmlElementStorage(final PathMacroSubstitutor pathMacroManager) {
    myPathMacroManager = pathMacroManager;
  }

  @Nullable
  protected abstract Document loadDocument() throws StateStorage.StateStorageException;

  @Nullable
  private synchronized Element getState(final String componentName) throws StateStorage.StateStorageException {
    final Element rootElement = getRootElement();
    if (rootElement == null) return null;

    final Element[] elements = JDOMUtil.getElements(rootElement);
    for (Element element : elements) {
      if (isComponentTag(componentName, element)) {
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

  private boolean isComponentTag(final String componentName, final Element element) {
    return element.getName().equals(COMPONENT) && Comparing.equal(element.getAttributeValue(NAME), componentName);
  }

  private synchronized void setState(final String componentName, final Element element) throws StateStorage.StateStorageException {
    if (element.getAttributes().isEmpty() && element.getChildren().isEmpty()) return;
    
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
      if (isComponentTag(componentName, e)) {
        e.detach();
      }
    }

    mySavedElements.add(newComponentElement);
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

  public boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException {
    final Element rootElement = getRootElement();
    if (rootElement == null) return false;

    final Element[] elements = JDOMUtil.getElements(rootElement);
    for (Element element : elements) {
      if (isComponentTag(componentName, element)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException {
    final Element element = getState(componentName);
    return DefaultStateSerializer.deserializeState(element, stateClass, mergeInto);
  }


   protected void sort() throws StateStorage.StateStorageException {
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

  public final void save() throws StateStorageException {
    try {
      if (!needsSave()) return;
      doSave();
    }
    finally {
      for (Element savedElement : mySavedElements) {
        savedElement.detach();
      }
      mySavedElements.clear();
    }
  }

  protected abstract void doSave() throws StateStorage.StateStorageException;

  @Nullable
  Element getRootElement() throws StateStorage.StateStorageException {
    final Document document = getDocument();
    return document != null ? document.getRootElement() : null;
  }

  public Document getDocument() throws StateStorage.StateStorageException {
    if (myDocument == null) {
      myDocument = loadDocument();

      final Element[] elements = JDOMUtil.getElements(myDocument.getRootElement());
      for (Element element : elements) {
        if (element.getName().equals("component") && element.getAttributes().size() == 1 && element.getAttribute("name") != null && element.getChildren().isEmpty()) {
          element.getParent().removeContent(element);
          continue;
        }

        for (String componentName : OBSOLETE_COMPONENT_NAMES) {
          if (isComponentTag(componentName, element)) {
            element.getParent().removeContent(element);
          }
        }
      }
    }

    return myDocument;
  }

  public void setDefaultState(final Element element) {
    myDocument = new Document(element);
  }
}
