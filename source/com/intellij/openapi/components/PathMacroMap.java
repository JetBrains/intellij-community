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

import java.io.File;
import java.util.*;

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

  public abstract String substitute(String text, boolean caseSensitive);

  public final void substitute(Element e, boolean caseSensitive) {
    List content = e.getContent();
    for (int i = 0; i < content.size(); i++) {
      Object o = content.get(i);
      if (o instanceof Element) {
        Element element = (Element)o;
        substitute(element, caseSensitive);
      }
      else if (o instanceof Text) {
        Text t = (Text)o;
        t.setText(substitute(t.getText(), caseSensitive));
      }
      else if (o instanceof Comment)  {
        Comment c = (Comment)o;
        c.setText(substitute(c.getText(), caseSensitive));
      }
      else {
        LOG.error("Wrong content: " + o.getClass());
      }
    }

    List attributes = e.getAttributes();
    for (Iterator i = attributes.iterator(); i.hasNext();) {
      Attribute attribute = (Attribute)i.next();
      attribute.setValue(substitute(attribute.getValue(), caseSensitive));
    }
  }

  public int size() {
    return myMacroMap.size();
  }

  public Set<Map.Entry<String, String>> entries() {
    return myMacroMap.entrySet();
  }

  public Set<String> keySet() {
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
