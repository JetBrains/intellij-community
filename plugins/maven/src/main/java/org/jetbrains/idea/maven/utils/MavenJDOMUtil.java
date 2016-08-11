/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import org.jdom.Element;
import org.jdom.IllegalNameException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class MavenJDOMUtil {
  @Nullable
  public static Element read(final VirtualFile file, @Nullable final ErrorHandler handler) {
    String text;

    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (!file.isValid()) return null;

      try {
        text = VfsUtil.loadText(file);
      }
      catch (IOException e) {
        if (handler != null) handler.onReadError(e);
        return null;
      }
    }
    finally {
      accessToken.finish();
    }

    return doRead(text, handler);
  }

  @Nullable
  public static Element read(byte[] bytes, @Nullable ErrorHandler handler) {
    return doRead(CharsetToolkit.bytesToString(bytes, EncodingRegistry.getInstance().getDefaultCharset()), handler);
  }

  @Nullable
  private static Element doRead(String text, final ErrorHandler handler) {
    final LinkedList<Element> stack = new LinkedList<>();

    final Element[] result = {null};
    XmlBuilderDriver driver = new XmlBuilderDriver(text);
    XmlBuilder builder = new XmlBuilder() {
      public void doctype(@Nullable CharSequence publicId, @Nullable CharSequence systemId, int startOffset, int endOffset) {
      }

      public ProcessingOrder startTag(CharSequence localName, String namespace, int startoffset, int endoffset, int headerEndOffset) {
        String name = localName.toString();
        if (StringUtil.isEmptyOrSpaces(name)) return ProcessingOrder.TAGS;

        Element newElement;
        try {
          newElement = new Element(name);
        }
        catch (IllegalNameException e) {
          newElement = new Element("invalidName");
        }

        Element parent = stack.isEmpty() ? null : stack.getLast();
        if (parent == null) {
          result[0] = newElement;
        }
        else {
          parent.addContent(newElement);
        }
        stack.addLast(newElement);

        return ProcessingOrder.TAGS_AND_TEXTS;
      }

      public void endTag(CharSequence localName, String namespace, int startoffset, int endoffset) {
        String name = localName.toString();
        if (StringUtil.isEmptyOrSpaces(name)) return;

        for (Iterator<Element> itr = stack.descendingIterator(); itr.hasNext(); ) {
          Element element = itr.next();

          if (element.getName().equals(name)) {
            while (stack.removeLast() != element) {}
            break;
          }
        }
      }

      public void textElement(CharSequence text, CharSequence physical, int startoffset, int endoffset) {
        stack.getLast().addContent(JDOMUtil.legalizeText(text.toString()));
      }

      public void attribute(CharSequence name, CharSequence value, int startoffset, int endoffset) {
      }

      public void entityRef(CharSequence ref, int startOffset, int endOffset) {
      }

      public void error(String message, int startOffset, int endOffset) {
        if (handler != null) handler.onSyntaxError();
      }
    };

    driver.build(builder);
    return result[0];
  }

  @Nullable
  public static Element findChildByPath(@Nullable Element element, String path) {
    int i = 0;
    while (element != null) {
      int dot = path.indexOf('.', i);
      if (dot == -1) {
        return element.getChild(path.substring(i));
      }

      element = element.getChild(path.substring(i, dot));
      i = dot + 1;
    }

    return null;
  }

  public static String findChildValueByPath(@Nullable Element element, String path, String defaultValue) {
    Element child = findChildByPath(element, path);
    if (child == null) return defaultValue;
    String childValue = child.getTextTrim();
    return childValue.isEmpty() ? defaultValue : childValue;
  }

  public static String findChildValueByPath(@Nullable Element element, String path) {
    return findChildValueByPath(element, path, null);
  }

  public static boolean hasChildByPath(@Nullable Element element, String path) {
    return findChildByPath(element, path) != null;
  }

  public static List<Element> findChildrenByPath(@Nullable Element element, String path, String subPath) {
    return collectChildren(findChildByPath(element, path), subPath);
  }

  public static List<String> findChildrenValuesByPath(@Nullable Element element, String path, String childrenName) {
    List<String> result = new ArrayList<>();
    for (Element each : findChildrenByPath(element, path, childrenName)) {
      String value = each.getTextTrim();
      if (!value.isEmpty()) {
        result.add(value);
      }
    }
    return result;
  }

  private static List<Element> collectChildren(@Nullable Element container, String subPath) {
    if (container == null) return Collections.emptyList();

    int firstDot = subPath.indexOf('.');

    if (firstDot == -1) {
      return container.getChildren(subPath);
    }

    String childName = subPath.substring(0, firstDot);
    String pathInChild = subPath.substring(firstDot + 1);

    List<Element> result = new ArrayList<>();

    for (Element each : container.getChildren(childName)) {
      Element child = findChildByPath(each, pathInChild);
      if (child != null) result.add(child);
    }
    return result;
  }

  public interface ErrorHandler {
    void onReadError(IOException e);

    void onSyntaxError();
  }
}
