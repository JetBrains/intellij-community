// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import org.jdom.Element;
import org.jdom.IllegalNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public final class MavenJDOMUtil {
  @Nullable
  public static Element read(final VirtualFile file, @Nullable final ErrorHandler handler) {

    Application app = ApplicationManager.getApplication();
    if (app == null || app.isDisposed()) {
      return null;
    }
    String text
      = ReadAction.compute(() -> {
      if (!file.isValid()) return null;

      try {
        return VfsUtilCore.loadText(file);
      }
      catch (IOException e) {
        if (handler != null) handler.onReadError(e);
        return null;
      }
    });

    return text == null ? null : doRead(text, handler);
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
      @Override
      public void doctype(@Nullable CharSequence publicId, @Nullable CharSequence systemId, int startOffset, int endOffset) {
      }

      @Override
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

      @Override
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

      @Override
      public void textElement(CharSequence text, CharSequence physical, int startoffset, int endoffset) {
        stack.getLast().addContent(JDOMUtil.legalizeText(text.toString()));
      }

      @Override
      public void attribute(CharSequence name, CharSequence value, int startoffset, int endoffset) {
      }

      @Override
      public void entityRef(CharSequence ref, int startOffset, int endOffset) {
      }

      @Override
      public void error(@NotNull String message, int startOffset, int endOffset) {
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
