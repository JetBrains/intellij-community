package org.hanuna.gitalk.commitmodel;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * @author erokhins
 */
public class HashTest {
    public void runStringTest(String strHash) {
        Hash hash = Hash.buildHash(strHash);
        assertEquals(strHash, hash.toStrHash());
    }

    @Test
    public void testBuildHash() throws Exception {
        runStringTest("0000f");
        runStringTest("ff01a");
        runStringTest("0000");
    }

    @Test
    public void testEquals() throws Exception {
        Hash hash1 = Hash.buildHash("adf");
        assertTrue(hash1.equals(hash1));
        assertFalse(hash1.equals(null));
        Hash hash2 = Hash.buildHash("adf");
        assertTrue(hash1.equals(hash2));
        assertTrue(hash1 == hash2);
    }
}
