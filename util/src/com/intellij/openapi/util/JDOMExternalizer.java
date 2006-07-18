/**
 * @author cdr
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class JDOMExternalizer {
  private JDOMExternalizer() {
  }

  public static void write(Element root, @NonNls String name, String value) {
    @NonNls Element element = new Element("setting");
    element.setAttribute("name", name);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  public static void write(Element root, @NonNls String name, boolean value) {
    write(root, name, Boolean.toString(value));
  }
  public static void write(Element root, String name, int value) {
    write(root, name, Integer.toString(value));
  }

  public static boolean readBoolean(Element root, @NonNls String name) {
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

  public static String readString(@NonNls Element root, @NonNls String name) {
    List list = root.getChildren("setting");
    for (Object aList : list) {
      @NonNls Element element = (Element)aList;
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, name)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }
}