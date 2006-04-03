package com.intellij.util.text;

import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TObjectHashingStrategy;

import java.io.File;

/**
 * @author max
 */
public class FilePathHashingStrategy implements TObjectHashingStrategy<String> {
  public int computeHashCode(final String s) {
    int h = 0;
    final int len = s.length();
    for( int i = 0; i < len; i++) {
      char c = normalizeChar(s.charAt(i));
      h = 31*h + c;
    }
    return h;
  }

  private static char normalizeChar(final char cc) {
    if (cc >= 'a' && cc <= 'z' || cc == '.' || cc == '/') return cc; // optimization
    if (cc >= 'A' && cc <= 'Z') return (char)(cc - 'A' + 'a');

    char c = SystemInfo.isFileSystemCaseSensitive ? cc : Character.toLowerCase(cc);
    return c == File.separatorChar ? '/' : c;
  }

  public boolean equals(final String s1, final String s2) {
    if(s1 == s2) return true;
    if(s1 == null || s2 == null) return false;
    if (s1.length() != s2.length()) return false;
    int len = s1.length();
    for (int i = 0; i < len; i++) {
      if (normalizeChar(s1.charAt(i)) != normalizeChar(s2.charAt(i))) return false;
    }

    return true;
  }
}
