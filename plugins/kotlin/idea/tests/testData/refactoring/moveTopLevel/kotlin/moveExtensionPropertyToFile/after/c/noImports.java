package c;

import b.DependencyKt;

class J {
    void bar() {
        DependencyKt.getTest(new a.Test());
        DependencyKt.setTest(new a.Test(), 0);
    }
}
