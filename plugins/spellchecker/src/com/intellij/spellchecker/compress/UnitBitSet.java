package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class UnitBitSet {

  public static final int MAX_CHARS_IN_WORD = 64;
  public static final int MAX_UNIT_VALUE = 255;

  byte[] b = new byte[MAX_CHARS_IN_WORD];

  public int getUnitValue(int number) {
    final int r = b[number] & 0xFF;
    assert r >= 0 && r <= MAX_UNIT_VALUE : "invalid unit value";
    return r;
  }

  public void setUnitValue(int number, int value) {
    //assert value >= 0 : "unit value is negative" + value;
    assert value <= MAX_UNIT_VALUE : "unit value is too big";
    b[number] = (byte)value;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof UnitBitSet)) return false;
    return Arrays.equals(b, ((UnitBitSet)obj).b);
  }

  @Override
  public String toString() {
    final StringBuilder s = new StringBuilder();
    for (int i = 0; i < b.length; i++) {
      s.append(Integer.toHexString((int)b[i] & 0xFF));
    }
    return s.toString();
  }

  public static UnitBitSet create(@NotNull UnitBitSet origin) {
    UnitBitSet r = new UnitBitSet();
    System.arraycopy(origin.b, 0, r.b, 0, r.b.length);
    return r;
  }

  public static UnitBitSet create(byte[] value) {
    final UnitBitSet r = new UnitBitSet();
    System.arraycopy(value, 0, r.b, 0, value.length);
    return r;
  }


  static public byte[] getBytes(UnitBitSet origin) {
    final byte[] r = new byte[origin.b[0] + 2];
    System.arraycopy(origin.b, 0, r, 0, r.length);
    return r;
  }

}
