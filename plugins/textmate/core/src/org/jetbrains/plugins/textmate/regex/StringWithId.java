package org.jetbrains.plugins.textmate.regex;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class StringWithId {
  public final Object id = new Object();
  public final byte[] bytes;

  public StringWithId(String string) {
    bytes = string.getBytes(StandardCharsets.UTF_8);
  }

  public StringWithId(CharSequence string) {
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
    return obj instanceof StringWithId && Arrays.equals(bytes, ((StringWithId)obj).bytes);
  }
}
