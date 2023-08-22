package org.jetbrains.plugins.textmate.regex;

import org.jcodings.specific.NonStrictUTF8Encoding;
import org.jcodings.specific.UTF8Encoding;
import org.jetbrains.annotations.NotNull;

public final class RegexUtil {
  private RegexUtil() {
  }

  public static int codePointOffsetByByteOffset(byte[] stringBytes, int byteOffset) {
    if (byteOffset <= 0) {
      return 0;
    }
    return NonStrictUTF8Encoding.INSTANCE.strLength(stringBytes, 0, byteOffset);
  }

  public static int byteOffsetByCharOffset(@NotNull CharSequence charSequence, int charOffset) {
    if (charOffset <= 0) {
      return 0;
    }
    int result = 0;
    int i = 0;
    while (i < charOffset) {
      result += UTF8Encoding.INSTANCE.codeToMbcLength(charSequence.charAt(i));
      i++;
    }
    return result;
  }

  @NotNull
  public static TextMateRange codePointsRangeByByteRange(byte[] bytes, @NotNull TextMateRange byteRange) {
    int startOffset = codePointOffsetByByteOffset(bytes, byteRange.start);
    int endOffset = codePointOffsetByByteOffset(bytes, byteRange.end);
    return new TextMateRange(startOffset, endOffset);
  }
}
