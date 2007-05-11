/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
@SuppressWarnings({"unchecked"})
public class JDomConvertingUtil {
  @NonNls private static final String COMPONENT_ELEMENT = "component";
  @NonNls private static final String OPTION_ELEMENT = "option";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String VALUE_ATTRIBUTE = "value";

  private JDomConvertingUtil() {
  }

  public static Document loadDocument(File file) throws QualifiedJDomException, IOException {
    try {
      return JDOMUtil.loadDocument(file);
    }
    catch (JDOMException e) {
      throw new QualifiedJDomException(e, file.getAbsolutePath());
    }
    catch (IOException e) {
      throw e;
    }
  }

  public static String getOptionValue(Element element, String optionName) {
    return JDOMExternalizerUtil.readField(element, optionName);
  }

  public static Condition<Element> createAttributeValueFilter(final @NonNls String name, final @NonNls String value) {
    return new Condition<Element>() {
      public boolean value(final Element element) {
        return value.equals(element.getAttributeValue(name));
      }
    };
  }

  public static Condition<Element> createOptionElementFilter(final @NonNls String optionName) {
    return Conditions.and(createElementNameFilter(OPTION_ELEMENT),
                          createAttributeValueFilter(NAME_ATTRIBUTE, optionName));
  }

  public static void copyAttributes(Element from, Element to) {
    final List<Attribute> attributes = from.getAttributes();
    for (Attribute attribute : attributes) {
      to.setAttribute(attribute.getName(), attribute.getValue());
    }
  }

  public static void copyChildren(Element from, Element to) {
    copyChildren(from, to, Condition.TRUE);
  }

  public static void copyChildren(Element from, Element to, Condition<Element> filter) {
    final List<Element> list = from.getChildren();
    for (Element element : list) {
      if (filter.value(element)) {
        to.addContent((Element)element.clone());
      }
    }
  }

  @Nullable
  public static Element findComponent(Element root, @NonNls String componentName) {
    final List<Element> list = root.getChildren(COMPONENT_ELEMENT);
    for (Element element : list) {
      if (componentName.equals(element.getAttributeValue(NAME_ATTRIBUTE))) {
        return element;
      }
    }
    return null;
  }

  public static Condition<Element> createElementNameFilter(final @NonNls String elementName) {
    return new Condition<Element>() {
      public boolean value(final Element element) {
        return elementName.equals(element.getName());
      }
    };
  }

  public static void removeChildren(final Element element, final Condition<Element> filter) {
    List<Element> toRemove = new ArrayList<Element>();
    final List<Element> list = element.getChildren();
    for (Element e : list) {
      if (filter.value(e)) {
        toRemove.add(e);
      }
    }
    for (Element e : toRemove) {
      element.removeContent(e);
    }
  }

  public static Element createOptionElement(String name, String value) {
    final Element element = new Element(OPTION_ELEMENT);
    element.setAttribute(NAME_ATTRIBUTE, name);
    element.setAttribute(VALUE_ATTRIBUTE, value);
    return element;
  }

  public static void addChildAfter(final Element parent, final Element child, final Condition<Element> filter) {
    List list = parent.getContent();
    for (int i = 0; i < list.size(); i++) {
      Object o = list.get(i);
      if (o instanceof Element && filter.value((Element)o)) {
        if (i < list.size() - 1) {
          parent.addContent(i + 1, child);
        }
        else {
          parent.addContent(child);
        }
        return;
      }
    }
  }

  public static Element createComponentElement(final String componentName) {
    final Element element = new Element(COMPONENT_ELEMENT);
    element.setAttribute(NAME_ATTRIBUTE, componentName);
    return element;
  }

  public static void addComponent(final Element root, final Element component) {
    String componentName = component.getAttributeValue(NAME_ATTRIBUTE);
    final Element old = findComponent(root, componentName);
    if (old != null) {
      root.removeContent(old);
    }

    for (int i = 0; i < root.getContent().size(); i++) {
      Object o = root.getContent().get(i);
      if (o instanceof Element) {
        Element element = (Element)o;
        if (element.getName().equals(COMPONENT_ELEMENT)) {
          final String name = element.getAttributeValue(NAME_ATTRIBUTE);
          if (componentName.compareTo(name) < 0) {
            root.addContent(i, component);
            return;
          }
        }
      }
    }
    root.addContent(component);
  }

  @Nullable
  public static Element findChild(Element parent, final Condition<Element> filter) {
    final List<Element> list = parent.getChildren();
    for (Element e : list) {
      if (filter.value(e)) {
        return e;
      }
    }
    return null;
  }
}
