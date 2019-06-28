package org.jetbrains.plugins.textmate.regex;

import com.google.common.base.Preconditions;
import org.jcodings.specific.UTF8Encoding;

public class RegexUtil {
  private RegexUtil() {
  }

  public static int charOffsetByByteOffset(byte[] stringBytes, int byteOffset) {
    if (byteOffset <= 0) {
      return 0;
    }
    Preconditions.checkPositionIndex(byteOffset, stringBytes.length);
    return UTF8Encoding.INSTANCE.strLength(stringBytes, 0, byteOffset);
  }

  public static int byteOffsetByCharOffset(String string, int charOffset) {
    if (charOffset <= 0) {
      return 0;
    }
    Preconditions.checkPositionIndex(charOffset, string.length());
    int result = 0;
    int i = 0;
    while (i < charOffset) {
      result += UTF8Encoding.INSTANCE.codeToMbcLength(string.charAt(i));
      i++;
    }
    return result;
  }
}
