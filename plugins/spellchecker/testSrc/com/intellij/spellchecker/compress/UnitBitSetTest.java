/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import junit.framework.TestCase;

import java.util.Random;

public class UnitBitSetTest extends TestCase {

  public void testUnitValue() {
    int bitsPerUnit = 256;
    for (int i = 0; i < bitsPerUnit - 1; i++) {
      UnitBitSet bs = new UnitBitSet();
      bs.setUnitValue(0, i);
      assertEquals(i, bs.getUnitValue(0));
      assertEquals(0, bs.getUnitValue(1));
    }
  }

  public void testCreateFromBitSet() {
    UnitBitSet bs1 = new UnitBitSet();
    bs1.setUnitValue(5, 255);
    UnitBitSet bs2 = UnitBitSet.create(bs1);
    assertEquals(bs2, bs1);
  }

  public void testCompressorWithRandomData() {
    int amount = 10;
    for (int i = 0; i < 1000; i++) {
      int[] values = getRandoms(UnitBitSet.MAX_UNIT_VALUE, amount);
      final UnitBitSet bitSet = new UnitBitSet();
      bitSet.setUnitValue(0, values.length);
      for (int i1 = 1, valuesLength = values.length; i1 < valuesLength; i1++) {
        int value = values[i1];
        bitSet.setUnitValue(i1, value);
      }
      final byte[] compressed = UnitBitSet.getBytes(bitSet);
      final UnitBitSet decompressed = UnitBitSet.create(compressed);
      boolean check = (bitSet.equals(decompressed));
      if (!check) {
        System.out.println("bitSet: " + bitSet);
        System.out.println("decompressed: " + decompressed);
        UnitBitSet.getBytes(bitSet);
        UnitBitSet.create(compressed);
      }
      assertEquals(bitSet, decompressed);
    }
  }

  private static int[] getRandoms(int max, int count) {
    int[] result = new int[count];
    for (int i = 0; i < count; i++) {
      result[i] = new Random().nextInt(max);
    }
    return result;
  }

}
