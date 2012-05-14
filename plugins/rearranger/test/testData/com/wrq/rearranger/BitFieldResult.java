/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.parboiled.common;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/** This class represents an arbitrarily sized bitfield that provides for efficient operations on large arrays of booleans. */
public class BitField {
// ------------------------------ FIELDS ------------------------------

  private final int[] data;
  private final int   length;

// -------------------------- STATIC METHODS --------------------------

  /**
   * ORs the given BitFields.
   *
   * @param a the first Field.
   * @param b the second Field.
   * @return the OR of of both fields.
   */
  public static BitField or(BitField a, BitField b) {
    BitField result = new BitField(a.length);
    for (int i = 0; i < result.data.length; i++) {
      result.data[i] = a.data[i] | b.data[i];
    }
    return result;
  }

  /**
   * XORs the given BitFields.
   *
   * @param a the first Field.
   * @param b the second Field.
   * @return the XOR of of both fields.
   */
  public static BitField xor(BitField a, BitField b) {
    BitField result = new BitField(a.length);
    for (int i = 0; i < result.data.length; i++) {
      result.data[i] = a.data[i] ^ b.data[i];
    }
    return result;
  }

  /**
   * Determines whether the AND of the two given BitFields is non-empty.
   *
   * @param a the first field.
   * @param b the second field.
   * @return a boolean indicating whether the two given fields overlap.
   */
  public static boolean overlap(BitField a, BitField b) {
    if ((a.data[0] & b.data[0]) > 0) {
      return true;
    }

    int l = a.data.length;
    for (int i = 1; i < l; i++) {
      if ((a.data[i] & b.data[i]) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parses the specified string into a BitField.
   *
   * @param str the string to parse
   * @return the created BitField
   * @throws IllegalArgumentException for non-wellformed input strings
   */
  public static BitField valueOf(String str) throws IllegalArgumentException {
    if (StringUtils.isEmpty(str)) {
      throw new IllegalArgumentException();
    }

    String[] s = str.split("|");
    if (s.length < 2) {
      throw new IllegalArgumentException();
    }

    BitField bf = new BitField(Integer.decode(s[0]));
    if (bf.data.length != s.length - 1) {
      throw new IllegalArgumentException();
    }

    for (int i = 0; i < bf.data.length; i++) {
      bf.data[i] = Integer.decode(s[i + 1]);
    }

    return bf;
  }

  /**
   * Returns a BitField with only those bits that are set in a but not in b.
   *
   * @param a the first field
   * @param b the second field
   * @return the substract field.
   */
  public static BitField substract(BitField a, BitField b) {
    return and(a, not(and(a, b)));
  }

  /**
   * Negates the given field.
   *
   * @param a the field to negate.
   * @return the bitwise NOT of the given field.
   */
  public static BitField not(BitField a) {
    BitField result = new BitField(a.length);
    for (int i = 0; i < result.data.length; i++) {
      result.data[i] = ~a.data[i];
    }
    return result;
  }

  /**
   * ANDs the given BitFields.
   *
   * @param a the first Field.
   * @param b the second Field.
   * @return the AND of of both fields.
   */
  public static BitField and(BitField a, BitField b) {
    BitField result = new BitField(a.length);
    for (int i = 0; i < result.data.length; i++) {
      result.data[i] = a.data[i] & b.data[i];
    }
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  /**
   * Creates a new BitField with the given number of bits.
   *
   * @param length the number of bits
   */
  public BitField(int length) {
    Preconditions.checkArgument(length > 0);
    this.length = length;
    this.data = new int[(length >> 5) + 1]; // the number of ints we need to store the field is length / 32
  }

  /**
   * Creates a new BitField initialized with the contents of the given BitField.
   *
   * @param field the BitField to initialize with
   */
  public BitField(BitField field) {
    this.length = field.getLength();
    this.data = field.data.clone();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  /**
   * Gets the indexes of the set bit as an array of int.
   *
   * @return a list of all indices of the set bits in this field.
   */
  public List<Integer> getBits() {
    List<Integer> bits = new ArrayList<Integer>();
    for (int i = 0; i < length; i++) {
      if (get(i)) {
        bits.add(i);
      }
    }
    return bits;
  }

  /**
   * @param ix the index of the bit to get.
   * @return the bit at the given position
   */
  public boolean get(int ix) {
    Preconditions.checkArgument(0 <= ix && ix < length);
    return ((data[ix >> 5] >> ix) & 0x1) != 0;
  }

  /** @return the number of bits in this field */
  public int getLength() {
    return length;
  }

// ------------------------ CANONICAL METHODS ------------------------

  /**
   * @param other another BitField to compare against.
   * @return a boolean indicating whether this BitField is equal to the given one.
   */
  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof BitField)) return false;

    BitField bitField = (BitField)other;
    if (bitField.length != length) return false;

    for (int i = 0; i < data.length; i++) {
      if (bitField.data[i] != data[i]) return false;
    }
    return true;
  }

  /** @return A hashcode for this BitField. */
  @Override
  public int hashCode() {
    int hash = 31 * length;
    for (int i = 0; i < data.length; i++) {
      hash ^= data[i] + i;
    }
    return hash;
  }

  /** @return A string representation of this field. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(length);
    for (int d : data) {
      sb.append('|');
      sb.append(d);
    }
    return sb.toString();
  }

// -------------------------- OTHER METHODS --------------------------

  /**
   * ANDs the given field into this one.
   *
   * @param field the other field to AND in.
   */
  public void and(BitField field) {
    for (int i = 0; i < data.length; i++) {
      data[i] &= field.data[i];
    }
  }

  /**
   * Determines whether this BitField fully covers the given BitField,
   * i.e. whether this BitField doesn't change when the given one is ORed in.
   *
   * @param field The field to check for inclusion for.
   * @return A boolean indicating whether all bits of the given field are also set in this field.
   */
  public boolean contains(BitField field) {
    for (int i = 0; i < data.length; i++) {
      int d = data[i];
      if (d != (d | field.data[i])) {
        return false;
      }
    }
    return true;
  }

  /** @return A boolean indicating whether no bit in the field is currently set. */
  public boolean isAllClear() {
    for (int d : data) {
      if (d != 0x00000000) {
        return false;
      }
    }
    return true;
  }

  /** @return A boolean indicating whether all bits in the field are currently set. */
  public boolean isAllSet() {
    for (int d : data) {
      if (d != 0xFFFFFFFF) {
        return false;
      }
    }
    return true;
  }

  /** Performs a bitwise NOT operation on this field. */
  public void not() {
    for (int i = 0; i < data.length; i++) {
      data[i] = ~data[i];
    }
  }

  /**
   * ORs the given field into this one.
   *
   * @param field the other field to OR in.
   */
  public void or(BitField field) {
    for (int i = 0; i < data.length; i++) {
      data[i] |= field.data[i];
    }
  }

  /**
   * Set the given bit.
   *
   * @param ix the index of the bit to set
   */
  public void set(int ix) {
    Preconditions.checkArgument(0 <= ix && ix < length);

    // fill up the unused bits with the highest bit
    int d = ix == length - 1 ? 0xFFFFFFFF << ix : 0x1 << ix;
    data[ix >> 5] |= d; // switch the respective bit "on" by ORing in the bit mask
  }

  /**
   * Sets the bit at the given index to the given value.
   *
   * @param value the value to set the bit to.
   * @param ix    the index of the bit to set.
   */
  public void set(boolean value, int ix) {
    if (value) {
      set(ix);
    }
    else {
      clear(ix);
    }
  }

  /**
   * Unsets the given bit.
   *
   * @param ix the index of the bit to clear
   */
  public void clear(int ix) {
    Preconditions.checkArgument(0 <= ix && ix < length);

    // fill up the unused bits with the highest bit
    int d = ix == length - 1 ? 0xFFFFFFFF << ix : 0x1 << ix;
    data[ix >> 5] &= ~d; // switch the respective bit "off" by ANDing with the inversed mask
  }

  /**
   * Sets all bits in the field to the given value.
   *
   * @param value The bit value to set all bits to.
   */
  public void setAll(boolean value) {
    int d = value ? 0xFFFFFFFF : 0x00000000;
    for (int i = 0; i < data.length; i++) {
      data[i] = d;
    }
  }

  /**
   * XORs the given field into this one.
   *
   * @param field the other field to XOR in.
   */
  public void xor(BitField field) {
    for (int i = 0; i < data.length; i++) {
      data[i] ^= field.data[i];
    }
  }
}


