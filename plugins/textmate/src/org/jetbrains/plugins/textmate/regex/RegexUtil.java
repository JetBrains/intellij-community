package org.jetbrains.plugins.textmate.regex;

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.TextRange;
import org.jcodings.specific.UTF8Encoding;
import org.jetbrains.annotations.NotNull;

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

  public static int byteOffsetByCharOffset(@NotNull CharSequence charSequence, int charOffset) {
    if (charOffset <= 0) {
      return 0;
    }
    Preconditions.checkPositionIndex(charOffset, charSequence.length());
    int result = 0;
    int i = 0;
    while (i < charOffset) {
      result += UTF8Encoding.INSTANCE.codeToMbcLength(charSequence.charAt(i));
      i++;
    }
    return result;
  }

  @NotNull
  public static TextRange charRangeByByteRange(byte[] bytes, @NotNull TextRange byteRange) {
    int startOffset = charOffsetByByteOffset(bytes, byteRange.getStartOffset());
    int endOffset = charOffsetByByteOffset(bytes, byteRange.getEndOffset());
    return TextRange.create(startOffset, endOffset);
  }
}
