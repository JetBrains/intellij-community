/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import org.jdom.Element;

import java.util.List;

public class JDOMExternalizer {
  public static void write(Element root, String name, String value) {
    Element element = new Element("setting");
    element.setAttribute("name", name);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  public static void write(Element root, String name, boolean value) {
    write(root, name, Boolean.toString(value));
  }
  public static void write(Element root, String name, int value) {
    write(root, name, Integer.toString(value));
  }

  public static boolean readBoolean(Element root, String name) {
    return Boolean.valueOf(readString(root, name)).booleanValue();
  }
  public static int readInteger(Element root, String name, int defaultValue) {
    try {
      return Integer.valueOf(readString(root, name)).intValue();
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static String readString(Element root, String name) {
    List list = root.getChildren("setting");
    for (int i = 0; i < list.size(); i++) {
      Element element = (Element)list.get(i);
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, name)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }
}