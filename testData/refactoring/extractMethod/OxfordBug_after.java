class OxfordBug {
    private int f(int m, int n) throws Exception {
        int i = 0;
        while (i < n) {
            i++;
            n = newMethod(m, n, i == m);
        }
        return n;
    }

    private int newMethod(int m, int n, boolean b) throws Exception {
        if (b) {
            n += m;
            throw new Exception("" + n);
        }
        return n;
    }
}