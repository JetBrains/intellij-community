class C {
    /**
     * @param i
     * @param s
     */
    void method(final int i, String... s) {
        System.out.println(s[i]);
    }

    {
        method(0, "a", "b", "c");
        method(0);
    }
}