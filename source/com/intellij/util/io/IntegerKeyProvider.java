package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Dmitry.Shtukenberg
 * Date: Apr 28, 2004
 * Time: 4:52:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class IntegerKeyProvider implements ByteBufferMap.KeyProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.StringKeyProvider");

  public static final IntegerKeyProvider INSTANCE = new IntegerKeyProvider();

  private IntegerKeyProvider() {
  }

  public int hashCode(Object key) {
    return key.hashCode();
  }

  public void write(DataOutput out, Object key) throws IOException {
    Integer k = (Integer)key;
    out.writeInt(k.intValue());
  }

  public int length(Object key) {
    return 4;
  }

  public Object get(DataInput in) throws IOException {
    return new Integer(in.readInt());
  }

  public boolean equals(DataInput in, Object key) throws IOException {
    return ((Integer)key).intValue() == in.readInt();
  }
}
