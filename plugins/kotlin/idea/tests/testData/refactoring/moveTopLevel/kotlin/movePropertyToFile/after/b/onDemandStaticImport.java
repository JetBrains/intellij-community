package b;

class J {
    void bar() {
        b.DependencyKt.setTest("");
        System.out.println(b.DependencyKt.getTest());
    }
}
