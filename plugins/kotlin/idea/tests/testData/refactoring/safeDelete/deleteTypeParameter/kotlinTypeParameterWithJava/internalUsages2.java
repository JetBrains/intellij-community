class B {
    void bar(A<String> a) {
        a.<Int>foo("123", 123)
    }
}