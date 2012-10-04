package org.hanuna.gitalk.gittree;

/**
 * @author erokhins
 */
public class SimpleLexer {
    private final String separator = "|-";
    private final String s;

    int k = 0;

    public SimpleLexer(String s) {
        this.s = s;
    }

    public String next() {
        int newK = s.indexOf(separator, k);
        if (newK == -1) {
            throw new IndexOutOfBoundsException("no more cells in string: " + s);
        }
        String s1 = s.substring(k, newK);
        k = newK + 2;
        return s1;
    }

    public String restString() {
        return s.substring(k);
    }

}
