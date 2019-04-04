/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexei Orischenko
 * @author vlan
 */
public class PythonStringUtil {

  private PythonStringUtil() {
  }


  @NotNull
  public static String removeFirstPrefix(@Nullable String s, String separator) {
    if (s != null) {
      int pos = s.indexOf(separator);
      if (pos != -1) {
        return s.substring(pos + separator.length());
      }
    }
    return "";
  }

  @NotNull
  public static String removeLastSuffix(@Nullable String s, String separator) {
    if (s != null) {
      int pos = s.lastIndexOf(separator);
      if (pos != -1) {
        return s.substring(0, pos);
      }
    }
    return "";
  }

  public static boolean isPath(@Nullable String s) {
    if (!StringUtil.isEmpty(s)) {
      s = ObjectUtils.assertNotNull(s);
      s = FileUtil.toSystemIndependentName(s);
      final List<String> components = StringUtil.split(s, "/");
      for (String name : components) {
        if (name == components.get(0) && SystemInfo.isWindows && name.endsWith(":")) {
          continue;
        }
        if (!PathUtil.isValidFileName(name)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isEmail(String s) {
    if (!StringUtil.isEmpty(s)) {
      Pattern p = Pattern.compile("^[\\w\\.-]+@([\\w\\-]+\\.)+[a-z]{2,4}$");
      Matcher m = p.matcher(s);
      return m.matches();
    }
    return false;
  }

  @NotNull
  public static String getFirstPrefix(String s, String separator) {
    if (s != null) {
      int pos = s.indexOf(separator);
      if (pos != -1) {
        return s.substring(0, pos);
      }
    }
    return s != null ? s : "";
  }

  public static String getLastSuffix(String s, String separator) {
    if (s != null) {
      int pos = s.lastIndexOf(separator);
      if (pos != -1) {
        return s.substring(pos + 1);
      }
    }
    return "";
  }

  /**
   * Replaces word after last dot from string s to newElementName.
   * For example 'yy.xxx.foo' can be renamed to 'yy.xxx.bar'
   *
   * @param s
   * @param newElementName
   * @return
   */
  public static String replaceLastSuffix(String s, String separator, String newElementName) {

    Pair<String, String> quotes = null;
    if (PyStringLiteralUtil.isQuoted(s)) {
      quotes = PyStringLiteralUtil.getQuotes(s);
      s = PyStringLiteralUtil.stripQuotesAroundValue(s);
    }

    s = removeLastSuffix(s, separator);
    if (s.length() > 0) {
      s += separator;
    }
    s += newElementName;
    if (quotes != null) {
      s = quotes.first + s + quotes.second;
    }
    return s;
  }


  public static TextRange lastSuffixTextRange(@NotNull String text, String separator) {
    int offset = text.lastIndexOf(separator) + 1;
    int length = text.length() - offset;

    return TextRange.from(offset + 1, length);
  }


  @Nullable
  public static String intersect(String fullName, String elementStringValue) {
    QualifiedName fullQName = QualifiedName.fromDottedString(fullName);
    QualifiedName stringQName = QualifiedName.fromDottedString(elementStringValue);
    String[] s1 = stringQName.getComponents().toArray(new String[stringQName.getComponentCount()]);
    String[] s2 = fullQName.getComponents().toArray(new String[fullQName.getComponentCount()]);

    for (int i = s1.length - 1; i >= 0; i--) {
      boolean flag = true;
      if (i > s2.length - 1) {
        continue;
      }
      for (int j = 0; j <= i; j++) {
        if (!s1[i - j].equals(s2[s2.length - j - 1])) {
          flag = false;
          break;
        }
      }
      if (flag) {
        StringBuilder res = new StringBuilder();
        for (int j = 0; j <= i; j++) {
          if (j > 0) {
            res.append(".");
          }
          res.append(s1[j]);
        }

        return res.toString();
      }
    }

    return null;
  }
}
