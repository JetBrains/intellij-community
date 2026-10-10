class J {
    interface Handler { void handle(int first, String second); }

    void caller(K k) {
        Handler handler = k::foo;
    }
}