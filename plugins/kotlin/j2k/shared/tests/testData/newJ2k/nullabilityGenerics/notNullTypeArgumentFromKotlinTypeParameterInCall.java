class J {
    Topic<String> topic = new Topic<>();

    void test(K k) {
        k.f(topic);
        k.<String>f(topic);
    }
}

class Topic<T> {
}