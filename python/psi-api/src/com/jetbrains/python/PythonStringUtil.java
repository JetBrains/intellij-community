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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.endsWith;
import static com.intellij.openapi.util.text.StringUtil.startsWith;

/**
 * @author Alexei Orischenko
 * @author vlan
 */
public class PythonStringUtil {
  private static final ImmutableList<String> QUOTES = ImmutableList.of("'''", "\"\"\"", "'", "\"");

  private PythonStringUtil() {
  }

  /**
   * 'text' => text
   * "text" => text
   * text => text
   * "text => "text
   *
   * @return string without heading and trailing pair of ' or "
   */
  @NotNull
  public static String getStringValue(@NotNull String s) {
    return getStringValueTextRange(s).substring(s);
  }


  public static TextRange getStringValueTextRange(@NotNull String s) {
    final Pair<String, String> quotes = getQuotes(s);
    if (quotes != null) {
      return TextRange.create(quotes.getFirst().length(), s.length() - quotes.getSecond().length());
    }
    return TextRange.allOf(s);
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
    if (isQuoted(s)) {
      quotes = getQuotes(s);
      s = stripQuotesAroundValue(s);
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


  /**
   * Handles unicode and raw strings
   *
   * @param text
   * @return false if no quotes found, true otherwise
   *         sdfs -> false
   *         ur'x' -> true
   *         "string" -> true
   */

  public static boolean isQuoted(@Nullable String text) {
    return text != null && getQuotes(text) != null;
  }

  /**
   * Handles unicode and raw strings
   *
   * @param text
   * @return open and close quote (including raw/unicode prefixes), null if no quotes present in string
   *         'string' -> (', ')
   *         UR"unicode raw string" -> (UR", ")
   */
  @Nullable
  public static Pair<String, String> getQuotes(@NotNull final String text) {
    boolean start = true;
    int pos = 0;
    for (int i = 0; i < text.length(); i++) {
      final char c = Character.toLowerCase(text.charAt(i));
      if (start) {
        if (c == 'u' || c == 'r' || c == 'b') {
          pos = i + 1;
        }
        else {
          start = false;
        }
      }
      else {
        break;
      }
    }
    final String prefix = text.substring(0, pos);
    final String mainText = text.substring(pos);
    for (String quote : QUOTES) {
      final Pair<String, String> quotes = getQuotes(mainText, prefix, quote);
      if (quotes != null) {
        return quotes;
      }
    }
    return null;
  }

  @Nullable
  private static Pair<String, String> getQuotes(@NotNull String text, @NotNull String prefix, @NotNull String quote) {
    final int length = text.length();
    final int n = quote.length();
    if (length >= 2 * n && text.startsWith(quote) && text.endsWith(quote)) {
      return Pair.create(prefix + text.substring(0, n), text.substring(length - n));
    }
    return null;
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
        StringBuilder res = new StringBuilder("");
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

  public static TextRange getTextRange(PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      final List<TextRange> ranges = ((PyStringLiteralExpression)element).getStringValueTextRanges();
      return ranges.get(0);
    }
    else {
      return new TextRange(0, element.getTextLength());
    }
  }

  @Nullable
  public static String getText(@Nullable PyExpression ex) {
    if (ex == null) {
      return null;
    }
    else {
      return ex.getText();
    }
  }

  @Nullable
  public static String getStringValue(@Nullable PsiElement o) {
    if (o == null) {
      return null;
    }
    if (o instanceof PyStringLiteralExpression) {
      PyStringLiteralExpression literalExpression = (PyStringLiteralExpression)o;
      return literalExpression.getStringValue();
    }
    else {
      return o.getText();
    }
  }

  public static String stripQuotesAroundValue(String text) {
    Pair<String, String> quotes = getQuotes(text);
    if (quotes == null) {
      return text;
    }

    return text.substring(quotes.first.length(), text.length() - quotes.second.length());
  }

  public static boolean isRawString(String text) {
    text = text.toLowerCase();
    text = StringUtil.trimStart(text, "u");
    return isStringPrefixedBy(text.toLowerCase(), "r");
  }


  private static boolean isStringPrefixedBy(String text, String prefix) {
    return (startsWith(text, prefix + "\"") && endsWith(text, "\"")) || (startsWith(text, prefix + "\'") && endsWith(text, "\'"));
  }
}
