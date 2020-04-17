package org.jetbrains.plugins.textmate.regex;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StringWithId {
  public final byte[] bytes;
  public final Object id;

  public StringWithId(String string) {
    bytes = string.getBytes(StandardCharsets.UTF_8);
    id = new Object();
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
