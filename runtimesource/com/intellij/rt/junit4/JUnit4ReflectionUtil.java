/*
 * User: anna
 * Date: 23-Jun-2009
 */
package com.intellij.rt.junit4;

import org.junit.runner.Description;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnit4ReflectionUtil {
  private JUnit4ReflectionUtil() {
  }

  public static String getClassName(Description description) {
    try {
      return description.getClassName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      return matcher.matches() ? matcher.group(2) : displayName;
    }
  }

  public static String getMethodName(Description description) {
    try {
      return description.getMethodName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      if (matcher.matches()) return matcher.group(1);
      return null;
    }
  }
}