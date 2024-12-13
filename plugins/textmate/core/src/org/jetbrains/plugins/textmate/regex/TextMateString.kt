package org.jetbrains.plugins.textmate.regex;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class TextMateString {
  public final Object id = new Object();
  public final byte[] bytes;

  public TextMateString(String string) {
    bytes = string.getBytes(StandardCharsets.UTF_8);
  }

  public TextMateString(CharSequence string) {
    ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(string));
    bytes = new byte[byteBuffer.remaining()];
    byteBuffer.get(bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof TextMateString && Arrays.equals(bytes, ((TextMateString)obj).bytes);
  }
}
