// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ZenCodingUtil {
  private static final char NUMBER_IN_ITERATION_PLACE_HOLDER = '$';
  private static final String SURROUNDED_TEXT_MARKER = "$#";

  private ZenCodingUtil() {
  }

  public static boolean containsSurroundedTextMarker(@NotNull String s) {
    return s.contains(SURROUNDED_TEXT_MARKER);
  }

  public static String replaceMarkers(String s, int numberInIteration, int totalIterations, @Nullable String surroundedText) {
    String by = Integer.toString(numberInIteration + 1);
    StringBuilder builder = new StringBuilder(s.length());
    int markerStartIndex = -1;
    int i = 0;
    int n = s.length();
    while (i <= n) {
      char c = i < n ? s.charAt(i) : 0;
      if (c == '\\' && i < n - 1) {
        i++;
        builder.append(s.charAt(i));
      }
      else if (c == NUMBER_IN_ITERATION_PLACE_HOLDER && (i == n - 1 || s.charAt(i + 1) != '#')) {
        if (markerStartIndex == -1) {
          markerStartIndex = i;
        }
      }
      else {
        int markersCount = i - markerStartIndex;
        if (markerStartIndex != -1) {
          // counter base
          boolean decrement = false;
          if(i < n && s.charAt(i) == '@') {
            i++;
            if (i < n && s.charAt(i) == '-') {
              decrement = true;
              i++;
            }
            StringBuilder base = new StringBuilder();
            while (i < n && Character.isDigit(s.charAt(i))) {
              base.append(s.charAt(i));
              i++;
            }
            int baseInt = StringUtil.parseInt(base.toString(), 0) - 1;
            baseInt = Math.max(baseInt, 0);
            int byInt = decrement
                        ? totalIterations - numberInIteration
                        : numberInIteration + 1;
            byInt += baseInt;
            by = Integer.toString(byInt);
          }
          for (int k = 0, m = markersCount - by.length(); k < m; k++) {
            builder.append('0');
          }
          builder.append(by);
          markerStartIndex = -1;
          c = i < n ? s.charAt(i) : 0;
        }
        if (i < n) {
          if (c == NUMBER_IN_ITERATION_PLACE_HOLDER && surroundedText != null) {
            builder.append(surroundedText);
            i++;
          }
          else {
            builder.append(c);
          }
        }
      }
      i++;
    }
    return builder.toString();
  }

  public static String getValue(String value, int numberInIteration, int totalIterations, String surroundedText) {
    String s = replaceMarkers(value, numberInIteration, totalIterations, surroundedText);
    return s.replace("\"", "&quot;");
  }

  public static boolean isXML11ValidQName(String str) {
    final int colon = str.indexOf(':');
    if (colon == 0 || colon == str.length() - 1) {
      return false;
    }
    if (colon > 0) {
      final String prefix = str.substring(0, colon);
      final String localPart = str.substring(colon + 1);
      return XML11Char.isXML11ValidNCName(prefix) && XML11Char.isXML11ValidNCName(localPart);
    }
    return XML11Char.isXML11ValidNCName(str);
  }

  public static boolean isHtml(CustomTemplateCallback callback) {
    FileType type = callback.getFileType();
    if (type == HtmlFileType.INSTANCE || type == XHtmlFileType.INSTANCE) {
      return true;
    }
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(HTMLLanguage.INSTANCE);
  }

  public static boolean checkFilterSuffix(@NotNull String suffix) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (suffix.equals(generator.getSuffix())) {
        return true;
      }
    }
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      if (suffix.equals(filter.getSuffix())) {
        return true;
      }
    }
    return false;
  }
}
