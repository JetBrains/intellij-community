/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.application.options;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.StringBuilderSpinAllocator;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class ReplacePathToMacroMap extends PathMacroMap {
  final Set<String> myUsedMacros = new HashSet<String>();
  List<String> myPathsIndex = null;
  private static final Comparator<String> PATHS_COMPARATOR = new Comparator<String>() {
    public int compare(final String o1, final String o2) {
      return o2.length() - o1.length();
    }
  };

  public void addMacroReplacement(String path, String macroName) {
    put(quotePath(path), "$" + macroName + "$");
  }

  public String substitute(String text, boolean caseSensitive) {
    for (final String path : getPathIndex()) {
      final String macro = get(path);
      text = replacePathMacro(text, path, macro, caseSensitive);
    }
    return text;
  }

  private String replacePathMacro(String text, String path, final String macro, boolean caseSensitive) {
    if (text.length() < path.length()) {
      return text;
    }

    final StringBuilder newText = StringBuilderSpinAllocator.alloc();
    try {
      final boolean isWindowsRoot = path.endsWith(":/");
      int i = 0;
      while (i < text.length()) {
        int occurrenceOfPath = caseSensitive ? text.indexOf(path, i) : StringUtil.indexOfIgnoreCase(text, path, i);
        if (occurrenceOfPath >= 0) {
          int endOfOccurence = occurrenceOfPath + path.length();
          if (!isWindowsRoot && endOfOccurence < text.length() && text.charAt(endOfOccurence) != '/') {
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
          if (macro == null) {
            return null;
          }
          newText.append(text.substring(i, occurrenceOfPath));
          newText.append(macro);
          logUsage(macro);
          i = occurrenceOfPath + path.length();
        }
      }
      return newText.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(newText);
    }
  }

  private void logUsage(String macroReplacement) {
    if (macroReplacement.length() >= 2 && macroReplacement.startsWith("$") && macroReplacement.endsWith("$")) {
      macroReplacement = macroReplacement.substring(1, macroReplacement.length() - 1);
    }
    myUsedMacros.add(macroReplacement);
  }

  public Set<String> getUsedMacroNames() {
    final Set<String> userMacroNames = PathMacrosImpl.getInstanceEx().getUserMacroNames();
    final Set<String> used = new HashSet<String>(myUsedMacros);
    used.retainAll(userMacroNames);
    return used;
  }

  private List<String> getPathIndex() {
    if (myPathsIndex == null || myPathsIndex.size() != size()) {
      myPathsIndex = new ArrayList<String>(keySet());
      // sort so that lenthy paths are traversed first
      // so from the 2 strings such that one is a substring of another the one that dominates is substituted first
      Collections.sort(myPathsIndex, PATHS_COMPARATOR);
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
