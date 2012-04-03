package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.endsWith;
import static com.intellij.openapi.util.text.StringUtil.startsWith;

/**
 * @author Alexei Orischenko
 *         Date: Nov 26, 2009
 */
public class PythonStringUtil {
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
    if ((s.charAt(0) == '\'' || s.charAt(0) == '"') && (s.charAt(0) == s.charAt(s.length() - 1)) && s.length() > 1) {
      return TextRange.create(1, s.length() - 1);
    }
    else {
      return TextRange.create(0, s.length());
    }
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
  public static String removeExtension(@NotNull String s) {
    return removeLastSuffix(s, ".");
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

  public static boolean isIdentifier(String s) {
    if (!StringUtil.isEmpty(s)) {
      return StringUtil.isJavaIdentifier(s);
    }
    return false;
  }

  public static boolean isDjangoTemplateFileName(String s) {
    if (!StringUtil.isEmpty(s)) {
      return PathUtil.isValidFileName(s);
    }
    return false;
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

  @Nullable
  public static String removePrefix(@Nullable String s, @Nullable String prefix) {
    if (s != null && prefix != null && s.startsWith(prefix)) {
      return s.substring(prefix.length());
    }
    return s;
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

  /**
   * Removes first prefix
   *
   * @param s
   * @param separator
   * @return
   * @see PythonStringUtil#removeFirstPrefix
   * @deprecated
   */
  public static String getFirstSuffix(String s, String separator) {
    if (s != null) {
      int pos = s.indexOf(separator);
      if (pos != -1) {
        return s.substring(pos + 1);
      }
    }
    return "";
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

    Character quote = null;
    if (isQuoted(s)) {
      quote = s.charAt(0);
      s = removeQuotes(s);
    }

    s = removeLastSuffix(s, separator);
    if (s.length() > 0) {
      s += separator;
    }
    s += newElementName;
    if (quote != null) {
      s = quote.charValue() + s + quote.charValue();
    }
    return s;
  }

  private static String removeQuotes(String text) {
    return text.substring(1, text.length() - 1);
  }

  public static TextRange lastSuffixTextRange(@NotNull String text, String separator) {
    int offset = text.lastIndexOf(separator) + 1;
    int length = text.length() - offset;

    return TextRange.from(offset + 1, length);
  }

  public static boolean isQuoted(@Nullable String text) {
    if (text == null) {
      return false;
    }
    if (text.length() > 1 && text.charAt(0) == text.charAt(text.length() - 1) && (text.charAt(0) == '\'' || text.charAt(0) == '"')) {
      return true;
    }
    return false;
  }

  @Nullable
  public static String intersect(String fullName, String elementStringValue) {
    PyQualifiedName fullQName = PyQualifiedName.fromDottedString(fullName);
    PyQualifiedName stringQName = PyQualifiedName.fromDottedString(elementStringValue);
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
        StringBuffer res = new StringBuffer("");
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

  public static String findTagNamePrefix(String text, int offsetInElement) {
    int i = offsetInElement - 1;
    while (i >= 0 && isIdentifier(text.substring(i, offsetInElement))) {
      i--;
    }
    int nameStart = i + 1;

    if (nameStart <= offsetInElement) {
      return text.substring(nameStart, offsetInElement);
    }
    else {
      return "";
    }
  }

  public static boolean isAfterTagStart(String text, int offsetInElement) {
    int i = offsetInElement - 1;
    while (i >= 0 && isIdentifier(text.substring(i, offsetInElement))) {
      i--;
    }
    while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
      i--;
    }

    if (i > 0 && (i + 1) < text.length() && "{%".equals(text.substring(i - 1, i + 1))) {
      return true;
    }
    return false;
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
  public static String getStringValue(@Nullable Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof PyStringLiteralExpression) {
      PyStringLiteralExpression literalExpression = (PyStringLiteralExpression)o;
      return literalExpression.getStringValue();
    }
    else if (o instanceof PyExpression) {
      return ((PyExpression)o).getText();
    }
    else if (o instanceof LookupElement) {
      return ((LookupElement)o).getLookupString();
    }
    else if (o instanceof PsiElement) {
      return ((PsiElement)o).getText();
    }
    else if (o instanceof String) {
      return getStringValue((String)o);
    }
    else {
      return o.toString();
    }
  }

  public static String stripQuotesAroundValue(String text) {
    if ((startsWith(text, "u\"") && endsWith(text, "\"")) || (startsWith(text, "u\'") && endsWith(text, "\'"))) {
      text = text.substring(1);
    }
    return StringUtil.stripQuotesAroundValue(text);
  }
}
