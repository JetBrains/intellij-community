package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hanuna.gitalk.common.MyAssert.myAssert;

/**
 * @author erokhins
 */
public class Hash {
    @NotNull
    private final byte[] data;
    private static final Map<Hash, Hash> cache = new HashMap<Hash, Hash>();

    private Hash(byte[] hash) {
        this.data = hash;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Hash) {
            Hash hash = (Hash) obj;
            return Arrays.equals(this.data, hash.data);
        }
        return false;
    }

    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @NotNull
    private static byte[] buildData(String inputStr) {
        // if length == 5, need 3 byte + 1 signal byte
        int length = inputStr.length();
        byte even = (byte) (length % 2);
        byte[] data = new byte[length / 2 + 1 + even];
        data[0] = even;
        try {
            for (int i = 0; i < length / 2; i++) {
                int k = Integer.parseInt(inputStr.substring(2 * i, 2 * i + 2), 16);
                data[i + 1] = (byte) (k - 128);
            }
            if (even == 1) {
                int k = Integer.parseInt(inputStr.substring(length - 1), 16);
                data[length / 2 + 1] = (byte) (k - 128);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad hash string: " + inputStr);
        }
        return data;
    }

    @NotNull
    public static Hash buildHash(String inputStr) {
        byte[] data = buildData(inputStr);
        Hash newHash = new Hash(data);
        if (cache.containsKey(newHash)) {
            System.out.println(newHash.toStrHash());
            return cache.get(newHash);
        } else {
            cache.put(newHash, newHash);
        }
        return newHash;
    }

    public String toStrHash() {
        myAssert(data.length > 0, "bad length Hash.data");
        byte even = data[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < data.length; i++) {
            int k1 = (data[i] + 128) / 16;
            int k2 = (data[i] + 128) % 16;
            char c1 = Character.forDigit(k1, 16);
            char c2 = Character.forDigit(k2, 16);
            if (i == data.length - 1 && even == 1) {
                sb.append(c2);
            } else {
                sb.append(c1).append(c2);
            }
        }
        return sb.toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(b).append(' ');
        }
        return sb.toString();
    }

}
