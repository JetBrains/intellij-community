/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.application.options;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ReplacePathToMacroMap extends PathMacroMap{

  final Set<String> myUsedMacros = new HashSet<String>();

  public ReplacePathToMacroMap() {
  }

  public void addMacroReplacement(String path, String macroName) {
    put(quotePath(path), "$" + macroName + "$");
  }

  public String substitute(String text, boolean caseSensitive) {
    Set set = myMacroMap.keySet();
    for (Iterator i = set.iterator(); i.hasNext();) {
      final String path = (String)i.next();
      final String macro = myMacroMap.get(path);
      text = replacePathMacro(text, path, macro, caseSensitive);
    }
    return text;
  }

  private String replacePathMacro(String text, String path, final String macro, boolean ignoreCase) {
    if (text.length() < path.length()) {
      return text;
    }

    String text1 = ignoreCase ? text.toLowerCase() : text;
    String path1 = ignoreCase ? path.toLowerCase() : path;
    StringBuffer newText = null;
    int i = 0;
    while (i < text1.length()) {
      int i1 = text1.indexOf(path1, i);
      if (i1 >= 0) {
        int endOfOccurence = i1 + path1.length();
        if (endOfOccurence < text1.length() && text1.charAt(endOfOccurence) != '/') {
          i = endOfOccurence;
          continue;
        }
      }
      if (i1 < 0) {
        if (newText == null) {
          return text;
        }
        newText.append(text.substring(i));
        break;
      }
      else {
        if (macro == null) {
          return null;
        }
        if (newText == null) {
          newText = new StringBuffer();
        }
        newText.append(text.substring(i, i1));
        newText.append(macro);
        logUsage(macro);
        i = i1 + path.length();
      }
    }
    return newText != null ? newText.toString() : "";
  }

  private void logUsage(String macroReplacement) {
    if (macroReplacement.length() >= 2 && macroReplacement.startsWith("$") && macroReplacement.endsWith("$")) {
      macroReplacement = macroReplacement.substring(1, macroReplacement.length() - 1);
    }
    myUsedMacros.add(macroReplacement);
  }

  public int size() {
    return myMacroMap.size();
  }

  public Set<String> getUsedMacroNames() {
    final Set<String> userMacroNames = PathMacros.getInstance().getUserMacroNames();
    final Set<String> used = new HashSet<String>(myUsedMacros);
    used.retainAll(userMacroNames);
    return used;
  }
}
