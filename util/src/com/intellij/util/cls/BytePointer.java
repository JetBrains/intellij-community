/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.cls;

/**
 *
 */
public class BytePointer {
  public final byte[] bytes;
  public int offset;

  public BytePointer(byte[] bytes, int offset) {
    this.bytes = bytes;
    this.offset = offset;
  }
}
