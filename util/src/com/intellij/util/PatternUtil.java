package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PatternUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.PatternUtil");
  private static final HashMap ourEscapeRulls = new HashMap();

  static {
    // '.' should be escaped first
    ourEscapeRulls.put("\\*", ".*");
    ourEscapeRulls.put("\\?", ".");
    escape2('+');
    escape2('(');
    escape2(')');
    escape2('[');
    escape2(']');
    escape2('/');
    escape2('^');
    escape3('$');
    escape2('{');
    escape2('}');
    escape2('|');
  }

  private static void escape2(char symbol) {
    ourEscapeRulls.put("\\" + symbol, "\\\\" + symbol);
  }

  private static void escape3(char symbol) {
    ourEscapeRulls.put("\\" + symbol, "\\\\\\" + symbol);
  }

  public static String convertToRegex(String mask) {
    String[] strings = mask.split("\\\\", -1);
    StringBuffer pattern = new StringBuffer();
    String separator = "";
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      string = string.replaceAll("\\.", "\\\\.");
      for (Iterator iterator = ourEscapeRulls.keySet().iterator(); iterator.hasNext();) {
        String toEscape = (String) iterator.next();
        string = string.replaceAll(toEscape, (String) ourEscapeRulls.get(toEscape));
      }
      pattern.append(separator);
      separator = "\\\\";
      pattern.append(string);
    }
    return pattern.toString();
  }

  public static Pattern fromMask(String mask) {
//    String pattern = mask.replaceAll("\\.", "\\.").replaceAll("\\*", ".*").replaceAll("\\?", ".");
    try {
      return Pattern.compile(convertToRegex(mask));
    } catch (PatternSyntaxException e) {
      LOG.error(mask, e);
      return Pattern.compile("");
    }
  }
}
