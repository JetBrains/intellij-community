package com.intellij.spellchecker.compress;

import junit.framework.TestCase;


public class EncodeAndCompressTest extends TestCase {

    public void testEncodeAndCompress() {
        Encoder encoder = new Encoder();
        String word = "example";
        UnitBitSet bs = encoder.encode(word, true);
        byte[] compressed = UnitBitSet.getBytes(bs);
        final UnitBitSet decompressed = UnitBitSet.create(compressed);
        assertEquals(bs,decompressed);
        String restored = encoder.decode(decompressed);
        assertEquals(word,restored);
    }

}
