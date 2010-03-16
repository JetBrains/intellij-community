package com.intellij.spellchecker.compress;

import com.intellij.spellchecker.compress.Compressor;
import com.intellij.spellchecker.compress.Encoder;
import com.intellij.spellchecker.compress.UnitBitSet;
import junit.framework.TestCase;


public class EncodeAndCompressTest extends TestCase {

    public void testEncodeAndCompress() {
        Encoder encoder = new Encoder();
        Compressor compressor = new Compressor(2);
        String word = "example";
        UnitBitSet bs = encoder.encode(word, true);
        byte[] compressed = compressor.compress(bs);
        final UnitBitSet decompressed = compressor.decompress(compressed);
        assertEquals(bs,decompressed);
        String restored = encoder.decode(decompressed);
        assertEquals(word,restored);
    }

}
