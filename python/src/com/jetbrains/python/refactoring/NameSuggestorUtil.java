package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 24, 2009
 * Time: 6:49:13 PM
 */
public class NameSuggestorUtil {
  private NameSuggestorUtil() {
  }

  private static String deleteNonLetterFromString(@NotNull final String string) {
    Pattern pattern = Pattern.compile("[^a-zA-Z_]");
    Matcher matcher = pattern.matcher(string);
    return matcher.replaceAll("");
  }

  @NotNull
  public static Collection<String> generateNames(@NotNull String name) {
    name = StringUtil.decapitalize(deleteNonLetterFromString(name.replace('.', '_')));
    if (name.startsWith("get")) {
      name = name.substring(3);
    }
    else if (name.startsWith("is")) {
      name = name.substring(2);
    }
    while (name.startsWith("_")) {
      name = name.substring(1);
    }
    final int length = name.length();
    final Collection<String> possibleNames = new HashSet<String>();
    for (int i = 0; i < length; i++) {
      if (Character.isLetter(name.charAt(i)) &&
          (i == 0 || name.charAt(i - 1) == '_' || Character.isLowerCase(name.charAt(i - 1)) && Character.isUpperCase(name.charAt(i)))) {
        final String candidate = StringUtil.decapitalize(toUnderscoreCase(name.substring(i)));
        possibleNames.add(candidate);
      }
    }
    return possibleNames;
  }

  public static Collection<String> generateNamesByType(@NotNull String name) {
    final Collection<String> possibleNames = new HashSet<String>();
    name = StringUtil.decapitalize(deleteNonLetterFromString(name.replace('.', '_')));
    name = toUnderscoreCase(name);
    possibleNames.add(name);
    possibleNames.add(name.substring(0, 1));
    return possibleNames;
  }

  @NotNull
  public static String toCamelCase(@NotNull final String name) {
    final StringBuilder buffer = new StringBuilder();
    boolean isUpperCase = true;
    final int length = name.length();
    for (int i = 0; i < length; i++) {
      final char ch = name.charAt(i);
      if (ch == '_' && (i + 1 < length && name.charAt(i + 1) == '_')) {
        continue;
      }
      if (i + 1 < length
          && (i > 0) && (ch == '_' && name.charAt(i - 1) != '_')) {
        isUpperCase = true;
        continue;
      }
      if (Character.isUpperCase(ch)) {
        isUpperCase = true;
      }
      buffer.append(isUpperCase ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
      isUpperCase = false;
    }
    return buffer.toString();
  }

  @NotNull
  public static String toUnderscoreCase(@NotNull final String name) {
    StringBuilder buffer = new StringBuilder();
    final int length = name.length();

    for (int i = 0; i < length; i++) {
      final char ch = name.charAt(i);
      if (ch != '-') {
        buffer.append(Character.toLowerCase(ch));
      }
      else {
        buffer.append("_");
      }

      if (Character.isLetterOrDigit(ch)) {
        if (Character.isUpperCase(ch)) {
          if (i + 2 < length) {
            final char chNext = name.charAt(i + 1);
            final char chNextNext = name.charAt(i + 2);

            if (Character.isUpperCase(chNext) && Character.isLowerCase(chNextNext)) {

              buffer.append('_');
            }
          }
        }
        else if (Character.isLowerCase(ch) || Character.isDigit(ch)) {
          if (i + 1 < length) {
            final char chNext = name.charAt(i + 1);
            if (Character.isUpperCase(chNext)) {
              buffer.append('_');
            }
          }
        }
      }
    }
    return buffer.toString();
  }
}
