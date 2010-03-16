package com.intellij.spellchecker.compress;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public class UnitBitSet extends BitSet {

  public int bitsPerUnit = 10;

  public UnitBitSet() {
  }

  public UnitBitSet(int value, boolean exact) {
    if (exact) {
      this.bitsPerUnit = value;
    }
    else {
      this.bitsPerUnit = new Double(Math.log(value) / Math.log(2)).intValue() + 1;
    }
  }

  public void inc() {
    boolean t = true;
    for (int i = 0; i <= size(); i++) {
      if (!get(i) && t) {
        set(i);
        t = false;
      }
      else if (get(i) && t) {
        set(i, false);
      }
    }
  }

  public int getUnitValue(int number) {
    int startIndex = number * bitsPerUnit;
    int result = 0;
    for (int i = 0; i < bitsPerUnit; i++) {
      if (get(startIndex + i)) {
        result += Math.pow(2, i);
      }
    }
    return result;
  }

  public void setUnitValue(int number, int value) {
    if (value > Math.pow(2, bitsPerUnit) - 1) {
      throw new IllegalArgumentException();
    }
    int startIndex = number * bitsPerUnit;
    for (int i = 0; i < bitsPerUnit; i++) {
      set(startIndex + i, value % 2 == 1);
      value /= 2;
    }
  }

  public void setBits(int... bits) {
    if (bits == null) {
      return;
    }
    for (int bit : bits) {
      set(bit);
    }
  }

  public void moveLeft(int n) {
    if (n <= 0) {
      return;
    }
    for (int i = 0; i < size() - n * bitsPerUnit; i++) {
      set(i, get(n * bitsPerUnit + i));
    }
    for (int i = size() - n * bitsPerUnit; i < size(); i++) {
      set(i, false);
    }
  }

  public void moveRight(int n) {
    if (n <= 0) {
      return;
    }
    for (int i = size() - 1; i >= 0; i--) {
      set(i + n * bitsPerUnit, get(i));
    }
    for (int i = 0; i < n * bitsPerUnit; i++) {
      set(i, false);
    }
  }


  public void iterateParUnits(@NotNull Consumer<Integer> consumer, int offset, boolean skipLastZero) {
    int units = size() / bitsPerUnit;
    for (int i = offset; i <= units; i++) {
      consumer.consume(getUnitValue(i));
      if (skipLastZero && nextSetBit(i) == -1) {
        break;
      }
    }
  }

  public static UnitBitSet create(int... bits) {
    UnitBitSet bs = new UnitBitSet();
    bs.setBits(bits);
    return bs;
  }

  public static UnitBitSet create(int bitsPerUnit, boolean exact, int... bits) {
    UnitBitSet bs = new UnitBitSet(bitsPerUnit, exact);
    bs.setBits(bits);
    return bs;
  }

  public static UnitBitSet create(@NotNull BitSet bitSet, int bitsPerUnit) {
    UnitBitSet result = new UnitBitSet(bitsPerUnit, true);
    result.or(bitSet);
    return result;
  }
}
