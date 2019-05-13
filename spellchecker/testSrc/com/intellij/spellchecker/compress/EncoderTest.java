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

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

public class EncoderTest {
  @Test
  public void testSimple() {
    Encoder encoder = new Encoder();
    final String wordToTest = "abc";
    final UnitBitSet bitSet = encoder.encode(wordToTest, true);
    assertNotNull(bitSet);
    assertIndices(bitSet, 1, 2, 3);
    byte[] compressed = bitSet.pack();
    assertEquals(1, compressed.length);
    assertEquals(wordToTest, encoder.decode(compressed));
  }

  private static void assertIndices(UnitBitSet bitSet, int... indices) {
    assertEquals(indices.length, bitSet.b.length);
    for (int i = 0; i < indices.length; i++) {
      int index = indices[i];
      assertEquals(index, bitSet.getUnitValue(i));
    }
  }

  @Test
  public void testDouble() {
    Encoder encoder = new Encoder();
    final String wordToTest = "aaa";
    final UnitBitSet bitSet = encoder.encode(wordToTest, true);
    assertNotNull(bitSet);
    assertEquals(1, encoder.getAlphabet().getLastIndexUsed());
    assertIndices(bitSet, 1, 1, 1);

    assertEquals(wordToTest, encoder.decode(bitSet.pack()));
  }

  @Test
  public void testLetterRepetition() {
    Encoder encoder = new Encoder();
    final String wordToTest = "aba";
    final UnitBitSet bitSet = encoder.encode(wordToTest, true);
    assertNotNull(bitSet);
    assertEquals(2, encoder.getAlphabet().getLastIndexUsed());
    assertIndices(bitSet, 1, 2, 1);

    assertEquals(wordToTest, encoder.decode(bitSet.pack()));
  }

  @Test
  public void testReverse() {
    Encoder encoder = new Encoder();
    final String wordToTest1 = "abc";
    final UnitBitSet bitSet = encoder.encode(wordToTest1, true);
    assertNotNull(bitSet);
    assertEquals(3, encoder.getAlphabet().getLastIndexUsed());
    assertIndices(bitSet, 1, 2, 3);

    byte[] pack = bitSet.pack();
    assertEquals(1, pack.length);
    assertEquals(wordToTest1, encoder.decode(pack));

    final String wordToTest2 = "cba";
    final UnitBitSet bitSet2 = encoder.encode(wordToTest2, true);
    assertEquals(3, encoder.getAlphabet().getLastIndexUsed());
    assertNotNull(bitSet2);
    assertIndices(bitSet2, 3, 2, 1);

    byte[] pack2 = bitSet2.pack();
    assertEquals(1, pack2.length);
    assertEquals(wordToTest2, encoder.decode(pack2));
  }

  @Test
  public void testWithPredefinedAlphabet() {
    @SuppressWarnings("SpellCheckingInspection") Encoder encoder = new Encoder(new Alphabet("abcdefghijklmnopqrst"));
    final String wordToTest1 = "asia";
    //letter 'a' will be added at the end
    final UnitBitSet bitSet = encoder.encode(wordToTest1, true);
    assertNotNull(bitSet);
    assertEquals(20, encoder.getAlphabet().getLastIndexUsed());
    assertIndices(bitSet, 1, 19, 9, 1);

    assertEquals(wordToTest1, encoder.decode(bitSet.pack()));
  }

  @Test
  public void testUnknown() {
    Encoder encoder = new Encoder(new Alphabet("abc"));
    final String wordToTest1 = "def";
    final UnitBitSet bitSet = encoder.encode(wordToTest1, true);
    assertEquals(bitSet, Encoder.WORD_OF_ENTIRELY_UNKNOWN_LETTERS);
  }
}
