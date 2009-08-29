package com.intellij.spellchecker.util;

import java.util.List;

/**
 * Text utility.
 */
public final class Strings {
  private Strings() {
  }

  public static boolean isCapitalized(String word) {
    if (word.length() == 0) return false;

    boolean lowCase = true;
    for (int i = 1; i < word.length() && lowCase; i++) {
      lowCase = Character.isLowerCase(word.charAt(i));
    }

    return Character.isUpperCase(word.charAt(0)) && lowCase;
  }

  public static boolean isUpperCase(String word) {
    boolean upperCase = true;
    for (int i = 0; i < word.length() && upperCase; i++) {
      upperCase = Character.isUpperCase(word.charAt(i));
    }

    return upperCase;
  }

  public static boolean isMixedCase(String word) {
    if (word.length() < 2) return false;

    String tail = word.substring(1);
    String lowerCase = tail.toLowerCase();
    return !tail.equals(lowerCase) && !isUpperCase(word);
  }

  public static String capitalize(String word) {
    if (word.length() == 0) return word;

    StringBuffer buf = new StringBuffer(word);
    buf.setCharAt(0, Character.toUpperCase(buf.charAt(0)));
    return buf.toString();
  }

  public static void capitalize(List<String> words) {
    for (int i = 0; i < words.size(); i++) {
      words.set(i, capitalize(words.get(i)));
    }
  }

  public static void upperCase(List<String> words) {
    for (int i = 0; i < words.size(); i++) {
      words.set(i, words.get(i).toUpperCase());
    }
  }
    

}
