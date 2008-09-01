/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.io;

import java.io.DataOutput;
import java.io.IOException;
import java.io.DataInput;

/**
 * @author peter
 */
public class BooleanDataDescriptor implements PersistentEnumerator.DataDescriptor<Boolean>{
  public static final BooleanDataDescriptor INSTANCE = new BooleanDataDescriptor();

  private BooleanDataDescriptor() {
  }

  public int getHashCode(final Boolean value) {
    return value.hashCode();
  }

  public boolean isEqual(final Boolean val1, final Boolean val2) {
    return val1.equals(val2);
  }

  public void save(final DataOutput out, final Boolean value) throws IOException {
    out.writeBoolean(value.booleanValue());
  }

  public Boolean read(final DataInput in) throws IOException {
    return in.readBoolean();
  }
}
