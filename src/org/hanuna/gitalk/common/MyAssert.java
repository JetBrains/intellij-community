package org.hanuna.gitalk.common;

/**
 * @author erokhins
 */
public class MyAssert extends RuntimeException {

    private MyAssert(String message) {
        super(message);
    }

    public static void myAssert(boolean itTrue, String message) {
        if (! itTrue) {
            throw new MyAssert(message);
        }
    }
}
