class OxfordBug {
    private int f(int m, int n) throws Exception {
        int i = 0;
        while (i < n) {
            i = newMethod(i, m, n);
        }
        return n;
    }

    private int newMethod(int i, int m, int n) throws Exception {
        i++;
        if (i == m) {
            n += m;
            throw new Exception("" + n);
        }
        return i;
    }
}