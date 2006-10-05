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

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import sun.reflect.Reflection;

import java.util.ArrayList;

@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMExternalizableStringList extends ArrayList<String> implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.JDOMExternalizableStringList");

  private static final String ATTR_LIST = "list";
  private static final String ATTR_LISTSIZE = "size";
  private static final String ATTR_ITEM = "item";
  private static final String ATTR_INDEX = "index";
  private static final String ATTR_CLASS = "class";
  private static final String ATTR_VALUE = "itemvalue";

  public void readExternal(Element element) throws InvalidDataException {
    clear();

    for (final Object o : element.getChildren()) {
      Element listElement = (Element)o;
      if (ATTR_LIST.equals(listElement.getName())) {
        String sizeString = listElement.getAttributeValue(ATTR_LISTSIZE);
        int listSize;
        try {
          listSize = Integer.parseInt(sizeString);
        }
        catch (NumberFormatException ex) {
          throw new InvalidDataException("Size " + sizeString + " found. Must be integer!");
        }
        for (int j = 0; j < listSize; j++) {
          add(null);
        }
        final ClassLoader classLoader = Reflection.getCallerClass(2).getClassLoader();
        for (final Object o1 : listElement.getChildren()) {
          Element listItemElement = (Element)o1;
          if (!ATTR_ITEM.equals(listItemElement.getName())) {
            throw new InvalidDataException(
              "Unable to read list item. Unknown element found: " + listItemElement.getName());
          }
          String itemIndexString = listItemElement.getAttributeValue(ATTR_INDEX);
          String itemClassString = listItemElement.getAttributeValue(ATTR_CLASS);
          Class itemClass;
          try {
            itemClass = Class.forName(itemClassString, true, classLoader);
          }
          catch (ClassNotFoundException ex) {
            throw new InvalidDataException(
              "Unable to read list item: unable to load class: " + itemClassString + " \n" + ex.getMessage());
          }

          String listItem = listItemElement.getAttributeValue(ATTR_VALUE);

          LOG.assertTrue(String.class.equals(itemClass));

          set(Integer.parseInt(itemIndexString), listItem);
        }
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    int listSize = size();
    Element listElement = new Element(ATTR_LIST);
    listElement.setAttribute(ATTR_LISTSIZE, Integer.toString(listSize));
    element.addContent(listElement);
    for (int i = 0; i < listSize; i++) {
      String listItem = get(i);
      if (listItem != null) {
        Element itemElement = new Element(ATTR_ITEM);
        itemElement.setAttribute(ATTR_INDEX, Integer.toString(i));
        itemElement.setAttribute(ATTR_CLASS, listItem.getClass().getName());
        itemElement.setAttribute(ATTR_VALUE, DefaultJDOMExternalizer.filterXMLCharacters(listItem));
        listElement.addContent(itemElement);
      }
    }
  }
}
