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

public class EncoderTest extends TestCase {


    public void testSimple() {
        Encoder encoder = new Encoder();
        final String wordToTest = "abc";
        final UnitBitSet bitSet = encoder.encode(wordToTest, true);
        assertEquals(3, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(3, bitSet.getUnitValue(0));
        assertEquals(1, bitSet.getUnitValue(1));
        assertEquals(1, bitSet.getUnitValue(2));
        assertEquals(2, bitSet.getUnitValue(3));
        assertEquals(3, bitSet.getUnitValue(4));

        assertEquals(wordToTest, encoder.decode(bitSet));
    }


    public void testDouble() {
        Encoder encoder = new Encoder();
        final String wordToTest = "aaa";
        final UnitBitSet bitSet = encoder.encode(wordToTest, true);
        assertEquals(1, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(3, bitSet.getUnitValue(0));
        assertEquals(1, bitSet.getUnitValue(1));
        assertEquals(1, bitSet.getUnitValue(2));
        assertEquals(1, bitSet.getUnitValue(3));
        assertEquals(1, bitSet.getUnitValue(4));

        assertEquals(wordToTest, encoder.decode(bitSet));
    }

    public void testLetterRepetition() {
        Encoder encoder = new Encoder();
        final String wordToTest = "aba";
        final UnitBitSet bitSet = encoder.encode(wordToTest, true);
        assertEquals(2, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(3, bitSet.getUnitValue(0));
        assertEquals(1, bitSet.getUnitValue(1));
        assertEquals(1, bitSet.getUnitValue(2));
        assertEquals(2, bitSet.getUnitValue(3));
        assertEquals(1, bitSet.getUnitValue(4));

        assertEquals(wordToTest, encoder.decode(bitSet));
    }

    public void testReverse() {
        Encoder encoder = new Encoder();
        final String wordToTest1 = "abc";
        final UnitBitSet bitSet = encoder.encode(wordToTest1, true);
        assertEquals(3, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(3, bitSet.getUnitValue(0));
        assertEquals(1, bitSet.getUnitValue(1));
        assertEquals(1, bitSet.getUnitValue(2));
        assertEquals(2, bitSet.getUnitValue(3));
        assertEquals(3, bitSet.getUnitValue(4));

        assertEquals(wordToTest1, encoder.decode(bitSet));

        final String wordToTest2 = "cba";
        final UnitBitSet bitSet2 = encoder.encode(wordToTest2, true);
        assertEquals(3, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(3, bitSet2.getUnitValue(0));
        assertEquals(3, bitSet2.getUnitValue(1));
        assertEquals(3, bitSet2.getUnitValue(2));
        assertEquals(2, bitSet2.getUnitValue(3));
        assertEquals(1, bitSet2.getUnitValue(4));

        assertEquals(wordToTest2, encoder.decode(bitSet2));
    }


    public void testWithPredefinedAlphabet() {
        Encoder encoder = new Encoder(new Alphabet("abcdefghijklmnopqrst"));
        final String wordToTest1 = "asia";
        //letter 'a' will be added at the end
        final UnitBitSet bitSet = encoder.encode(wordToTest1, true);
        assertEquals(20, encoder.getAlphabet().getLastIndexUsed());
        assertEquals(4, bitSet.getUnitValue(0));
        assertEquals(1, bitSet.getUnitValue(1));
        assertEquals(1, bitSet.getUnitValue(2));
        assertEquals(19, bitSet.getUnitValue(3));
        assertEquals(9, bitSet.getUnitValue(4));
        assertEquals(1, bitSet.getUnitValue(5));

        assertEquals(wordToTest1, encoder.decode(bitSet));


    }


}
