class C {
    void <caret>method() {
    }
}

class C1 extends C {
    void method() {
    }
}

class Usage {
    {
        new C().method();
        new C1().method();
    }
}