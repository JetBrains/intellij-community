/*
 * @author max
 */
package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class StringRef {
  private int id;
  private String name;
  private PersistentStringEnumerator store;

  private StringRef(final String name) {
    this.name = name;
    this.id = -1;
  }

  private StringRef(final int id, final PersistentStringEnumerator store) {
    this.id = id;
    this.store = store;
    this.name = null;
  }

  public String getString() {
    if (name == null) {
      try {
        name = store.valueOf(id);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return name;
  }

  public void writeTo(DataOutput out, PersistentStringEnumerator store) throws IOException {
    int nameId = getId(store);
    out.writeByte(nameId & 0xFF);
    DataInputOutputUtil.writeINT(out, (nameId >> 8));
  }

  public int getId(PersistentStringEnumerator store) {
    if (id == -1) {
      try {
        id = store.enumerate(name);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return id;
  }

  public String toString() {
    return getString();
  }

  public int length() {
    return getString().length();
  }

  public int hashCode() {
    return toString().hashCode();
  }

  public boolean equals(final Object that) {
    return that == this || that instanceof StringRef && toString().equals(that.toString());
  }

  public static String toString(StringRef ref) {
    return ref != null ? ref.getString(): null;
  }

  public static StringRef fromString(String source) {
    return source == null ? null : new StringRef(source);
  }

  public static StringRef fromStream(DataInput in, PersistentStringEnumerator store) throws IOException {
    final int low = in.readUnsignedByte();
    final int nameId = (DataInputOutputUtil.readINT(in) << 8) | low;

    return nameId != 0 ? new StringRef(nameId, store) : null;
  }

  private final static StringRef[] EMPTY_ARRAY = new StringRef[0];
  public static StringRef[] createArray(int count) {
    return count == 0 ? EMPTY_ARRAY : new StringRef[count];
  }

}