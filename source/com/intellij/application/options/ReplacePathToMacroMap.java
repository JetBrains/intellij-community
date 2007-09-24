/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.application.options;

import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ReplacePathToMacroMap extends PathMacroMap {
  private List<String> myPathsIndex = null;

  private static final Comparator<Map.Entry<String, String>> PATHS_COMPARATOR = new Comparator<Map.Entry<String, String>>() {
    public int compare(final Map.Entry<String, String> o1, final Map.Entry<String, String> o2) {
      int idx1 = getIndex(o1);
      int idx2 = getIndex(o2);

      if (idx1 != idx2) return idx1 - idx2;

      return o2.getKey().length() - o1.getKey().length();
    }

    private int getIndex(final Map.Entry<String, String> s) {
      if (s.getValue().indexOf("..") >= 0) return 3;

      if (s.getValue().indexOf("$MODULE_DIR$") >= 0) return 1;
      if (s.getValue().indexOf("$PROJECT_DIR$") >= 0) return 1;
      return 2;
    }
  };

  public void addMacroReplacement(String path, String macroName) {
    put(quotePath(path), "$" + macroName + "$");
  }

  public String substitute(String text, boolean caseSensitive, final Set<String> usedMacros) {
    for (final String path : getPathIndex()) {
      final String macro = get(path);
      text = replacePathMacro(text, path, macro, caseSensitive, usedMacros);
    }
    return text;
  }

  private static String replacePathMacro(String text, String path, final String macro, boolean caseSensitive, final Set<String> usedMacros) {
    if (text.length() < path.length()) {
      return text;
    }

    if (path.length() == 0) return text;

    final StringBuilder newText = StringBuilderSpinAllocator.alloc();
    try {
      final boolean isWindowsRoot = path.endsWith(":/");
      int i = 0;
      while (i < text.length()) {
        int occurrenceOfPath = caseSensitive ? text.indexOf(path, i) : StringUtil.indexOfIgnoreCase(text, path, i);
        if (occurrenceOfPath >= 0) {
          int endOfOccurence = occurrenceOfPath + path.length();
          if (!isWindowsRoot && endOfOccurence < text.length() && text.charAt(endOfOccurence) != '/' && !text.substring(endOfOccurence).startsWith("!/")) {
            i = endOfOccurence;
            continue;
          }
        }
        if (occurrenceOfPath < 0) {
          if (newText.length() == 0) {
            return text;
          }
          newText.append(text.substring(i));
          break;
        }
        else {
          newText.append(text.substring(i, occurrenceOfPath));
          newText.append(macro);
          logUsage(macro, usedMacros);
          i = occurrenceOfPath + path.length();
        }
      }
      return newText.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(newText);
    }
  }

  private static void logUsage(String macroReplacement, final Set<String> usedMacros) {
    if (usedMacros == null) return;

    if (macroReplacement.length() >= 2 && macroReplacement.startsWith("$") && macroReplacement.endsWith("$")) {
      macroReplacement = macroReplacement.substring(1, macroReplacement.length() - 1);
    }

    usedMacros.add(macroReplacement);
  }

  public List<String> getPathIndex() {
    if (myPathsIndex == null || myPathsIndex.size() != size()) {

      final Set<Map.Entry<String, String>> entrySet = entries();
      Map.Entry<String, String>[] entries = entrySet.toArray(new Map.Entry[entrySet.size()]);
      Arrays.sort(entries, PATHS_COMPARATOR);
      myPathsIndex = new ArrayList<String>(entries.length);

      for (Map.Entry<String, String> entry : entries) {
        myPathsIndex.add(entry.getKey());
      }
    }
    return myPathsIndex;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ReplacePathToMacroMap)) return false;

    return myMacroMap.equals(((ReplacePathToMacroMap)obj).myMacroMap);
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }
}
