/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public abstract class PathMacroMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.PathMacroMap");
  protected final Map<String, String> myMacroMap;

  protected PathMacroMap() {
    myMacroMap = new LinkedHashMap<String,String>();
  }

  public void putAll(PathMacroMap pathMacroMap) {
    putAll(pathMacroMap.myMacroMap);
  }

  public void putAll(Map<String, String> macroMap) {
    myMacroMap.putAll(macroMap);
  }

  public void put(String fromText, String toText) {
    myMacroMap.put(fromText, toText);
  }

  @SuppressWarnings({"WeakerAccess"})
  public abstract String substitute(String text, boolean caseSensitive, @Nullable final Set<String> usedMacros);

  public final void substitute(Element e, boolean caseSensitive, @Nullable final Set<String> usedMacros) {
    List content = e.getContent();
    for (Object child : content) {
      if (child instanceof Element) {
        Element element = (Element)child;
        substitute(element, caseSensitive, usedMacros);
      }
      else if (child instanceof Text) {
        Text t = (Text)child;
        t.setText(substitute(t.getText(), caseSensitive, usedMacros));
      }
      else if (child instanceof Comment) {
        Comment c = (Comment)child;
        c.setText(substitute(c.getText(), caseSensitive, usedMacros));
      }
      else {
        LOG.error("Wrong content: " + child.getClass());
      }
    }

    List attributes = e.getAttributes();
    for (final Object attribute1 : attributes) {
      Attribute attribute = (Attribute)attribute1;
      attribute.setValue(substitute(attribute.getValue(), caseSensitive, usedMacros));
    }
  }

  public final void substitute(org.w3c.dom.Element e, boolean caseSensitive,@Nullable final Set<String> usedMacros) {
    final NodeList childNodes = e.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      final Node child = childNodes.item(i);

      if (child instanceof org.w3c.dom.Element) {
        org.w3c.dom.Element element = (org.w3c.dom.Element)child;
        substitute(element, caseSensitive, usedMacros);
      }
      else if (child instanceof org.w3c.dom.Text) {
        org.w3c.dom.Text t = (org.w3c.dom.Text)child;
        t.setTextContent(substitute(t.getTextContent(), caseSensitive, usedMacros));
      }
      else if (child instanceof org.w3c.dom.Comment) {
        org.w3c.dom.Comment c = (org.w3c.dom.Comment)child;
        c.setTextContent(substitute(c.getTextContent(), caseSensitive, usedMacros));
      }
      else {
        LOG.error("Wrong content: " + child.getClass());
      }
    }

    final NamedNodeMap attributes = e.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      final Node node = attributes.item(i);
      Attr attr = (Attr)node;
      e.setAttribute(attr.getName(), substitute(attr.getValue(), caseSensitive, usedMacros));
    }
  }

  public int size() {
    return myMacroMap.size();
  }

  protected Set<Map.Entry<String, String>> entries() {
    return myMacroMap.entrySet();
  }

  protected Set<String> keySet() {
    return myMacroMap.keySet();
  }

  public String get(String key) {
    return myMacroMap.get(key);
  }

  public static String quotePath(String path) {
    path = path.replace(File.separatorChar, '/');
    path = StringUtil.replace(path, "&", "&amp;");
    return path;
  }

}
