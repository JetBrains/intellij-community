// IGNORE_K2
class C {
    void foo(Object o) {
        if (!(o instanceof String)) return;
        System.out.println("String");
    }
}