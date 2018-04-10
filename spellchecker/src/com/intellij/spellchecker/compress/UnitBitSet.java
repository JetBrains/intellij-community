/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

class UnitBitSet {
  static final int MAX_CHARS_IN_WORD = 64;
  static final int MAX_UNIT_VALUE = 255;

  final byte[] b;
  private final Alphabet alpha;

  UnitBitSet(@NotNull byte[] indices, @NotNull Alphabet alphabet) {
    b = indices;
    alpha = alphabet;
  }

  int getUnitValue(int number) {
    final int r = b[number] & 0xFF;
    assert r >= 0 && r <= MAX_UNIT_VALUE : "invalid unit value";
    return r;
  }

  void setUnitValue(int number, int value) {
    assert value >= 0 : "unit value is negative" + value;
    assert value <= MAX_UNIT_VALUE : "unit value is too big";
    b[number] = (byte)value;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UnitBitSet && Arrays.equals(b, ((UnitBitSet)obj).b);
  }

  @Override
  public String toString() {
    final StringBuilder s = new StringBuilder();
    for (byte aB : b) {
      s.append(Integer.toHexString((int)aB & 0xFF));
    }
    return s.toString();
  }

  @NotNull
  public byte[] pack() {
    int meaningfulBits = 32 - Integer.numberOfLeadingZeros(alpha.getLastIndexUsed());
    assert meaningfulBits <= 8 && meaningfulBits >= 1 : meaningfulBits + ": "+alpha;
    byte[] result = new byte[(b.length * meaningfulBits + 7) / 8];

    int byteNumber = 0;
    int bitOffset = 0;

    for (byte index : b) {
      int bitsToChip = Math.min(8 - bitOffset, meaningfulBits);
      result[byteNumber] |= (index & ((1 << bitsToChip) - 1)) << bitOffset;

      int bitsLeft = meaningfulBits - bitsToChip;
      if (bitsLeft > 0) {
        byteNumber++;
        result[byteNumber] |= (index >> bitsToChip) & ((1 << bitsLeft) - 1);
        bitOffset = bitsLeft;
      }
      else {
        bitOffset += bitsToChip;
      }
    }
    return result;
  }

  @NotNull
  public static String decode(@NotNull byte[] packed, @NotNull Alphabet alphabet) {
    return decode(packed, 0, packed.length, alphabet);
  }

  @NotNull
  public static String decode(@NotNull byte[] packed, int from, int to, @NotNull Alphabet alphabet) {
    int meaningfulBits = 32 - Integer.numberOfLeadingZeros(alphabet.getLastIndexUsed());
    assert meaningfulBits <= 8 : alphabet;

    StringBuilder result = new StringBuilder((to - from) * 8 / meaningfulBits);

    int curByte = packed[from];
    int byteIndex = from;
    int bitOffset = 0;

    while (byteIndex < to) {
      int index = curByte & ((1 << meaningfulBits) - 1);
      char letter = alphabet.getLetter(index);
      if (letter == '\u0000') {
        break;
      }
      result.append(letter);

      curByte >>>= meaningfulBits;
      bitOffset += meaningfulBits;
      assert bitOffset <= 8 : alphabet;
      if (bitOffset + meaningfulBits > 8) {
        if (++byteIndex == to) break;
        int leftOverBits = 8 - bitOffset;
        curByte = packed[byteIndex] << leftOverBits | (curByte & ((1 << leftOverBits) - 1));
        bitOffset = -leftOverBits;
      }
    }
    return result.toString();
  }

  static int getFirstLetterIndex(byte firstPackedByte, @NotNull Alphabet alphabet) {
    int meaningfulBits = 32 - Integer.numberOfLeadingZeros(alphabet.getLastIndexUsed());
    assert meaningfulBits <= 8 : alphabet;

    int index = firstPackedByte & ((1 << meaningfulBits) - 1);
    return index;
  }
}
