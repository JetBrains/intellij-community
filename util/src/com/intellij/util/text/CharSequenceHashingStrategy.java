package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class CharSequenceHashingStrategy implements TObjectHashingStrategy<CharSequence> {
  public int computeHashCode(final CharSequence s) {
    return StringUtil.stringHashCode(s);
  }

  public boolean equals(final CharSequence s1, final CharSequence s2) {
    if (s1.length() != s2.length()) return false;
    int len = s1.length();
    for (int i = 0; i < len; i++) {
      if (s1.charAt(i) != s2.charAt(i)) return false;
    }
    return true;
  }
}
