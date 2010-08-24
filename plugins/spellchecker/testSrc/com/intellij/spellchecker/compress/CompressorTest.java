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

public class CompressorTest extends TestCase {

    public void testCompressorWithDefinedData() {
        Compressor compressor = new Compressor(0);
        UnitBitSet bs = UnitBitSet.create(1, 3, 5, 10);
        final byte[] compressed = compressor.compress(bs);
        assertEquals(3, compressed.length);
        assertEquals(42, compressed[1]);
        assertEquals(8, compressed[2]);
        final UnitBitSet restored = compressor.decompress(compressed);
        assertEquals(bs, restored);
    }

    public void testCompressorWithRandomData() {
        Compressor compressor = new Compressor(0);
        int max = 10;
        for (int i = 0; i < 1000; i++) {
            int[] values = getRandoms(1000, max);
            final UnitBitSet bitSet = new UnitBitSet();
            for (int value : values) {
                bitSet.set(value);
            }
            final byte[] compressed = compressor.compress(bitSet);
            final UnitBitSet decompressed = compressor.decompress(compressed);
            boolean check = (bitSet.equals(decompressed));
            if (!check) {
                System.out.println("bitSet: " + bitSet);
                System.out.println("decompressed: " + decompressed);
                compressor.compress(bitSet);
                compressor.decompress(compressed);
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
