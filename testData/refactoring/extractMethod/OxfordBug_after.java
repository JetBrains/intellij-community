class OxfordBug {
    private int f(int m, int n) throws Exception {
        int i = 0;
        while (i < n) {
            i = newMethod(m, n, i);
        }
        return n;
    }

    private int newMethod(int m, int n, int i) throws Exception {
        i++;
        if (i == m) {
            n += m;
            throw new Exception("" + n);
        }
        return i;
    }
}