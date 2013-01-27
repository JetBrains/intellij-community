/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingUtil {
  private static final char NUMBER_IN_ITERATION_PLACE_HOLDER = '$';
  private static final String SURROUNDED_TEXT_MARKER = "$#";

  private ZenCodingUtil() {
  }

  public static boolean containsSurroundedTextMarker(@NotNull String s) {
    return s.contains(SURROUNDED_TEXT_MARKER);
  }

  public static String replaceMarkers(String s, int numberInIteration, @Nullable String surroundedText) {
    final String by = Integer.toString(numberInIteration + 1);
    StringBuilder builder = new StringBuilder(s.length());
    int j = -1;
    int i = 0;
    int n = s.length();
    while (i <= n) {
      char c = i < n ? s.charAt(i) : 0;
      if (c == NUMBER_IN_ITERATION_PLACE_HOLDER && (i == n - 1 || s.charAt(i + 1) != '#')) {
        if (j == -1) {
          j = i;
        }
      }
      else {
        if (j != -1) {
          for (int k = 0, m = i - j - by.length(); k < m; k++) {
            builder.append('0');
          }
          builder.append(by);
          j = -1;
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

  public static String getValue(String value, int numberInIteration, String surroundedText) {
    String s = replaceMarkers(value, numberInIteration, surroundedText);
    return s.replace("\"", "&quot;");
  }

  public static int parseNonNegativeInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Throwable ignored) {
    }
    return -1;
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
    return type == StdFileTypes.HTML || type == StdFileTypes.XHTML;
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
