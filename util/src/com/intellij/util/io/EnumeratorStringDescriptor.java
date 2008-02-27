package com.intellij.util.io;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 18, 2007
*/
public class EnumeratorStringDescriptor implements PersistentEnumerator.DataDescriptor<String> {
  public static final EnumeratorStringDescriptor INSTANCE = new EnumeratorStringDescriptor();
  private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();

  public int getHashCode(final String value) {
    return value.hashCode();
  }

  public boolean isEqual(final String val1, final String val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput storage, final String value) throws IOException {
    IOUtil.writeUTFFast(buffer, storage, value);
  }

  public String read(final DataInput storage) throws IOException {
    return IOUtil.readUTFFast(buffer, storage);
  }
}
