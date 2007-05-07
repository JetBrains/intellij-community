package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class CaseInsensitiveStringHashingStrategy implements TObjectHashingStrategy<String> {
  public static final CaseInsensitiveStringHashingStrategy INSTANCE = new CaseInsensitiveStringHashingStrategy();
  
  public int computeHashCode(final String s) {
    return StringUtil.stringHashCodeInsensitive(s);
  }

  public boolean equals(final String s1, final String s2) {
    return s1.equalsIgnoreCase(s2);
  }
}
