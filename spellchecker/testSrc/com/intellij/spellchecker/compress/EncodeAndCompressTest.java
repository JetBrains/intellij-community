package com.intellij.spellchecker.compress;

import junit.framework.TestCase;


public class EncodeAndCompressTest extends TestCase {

    public void testEncodeAndCompress() {
        Encoder encoder = new Encoder();
        String word = "example";
        UnitBitSet bs = encoder.encode(word, true);
        byte[] compressed = bs.pack();
        final String decompressed = UnitBitSet.decode(compressed, encoder.getAlphabet());
        assertEquals(word,decompressed);
        String restored = encoder.decode(compressed);
        assertEquals(word,restored);
    }

}
