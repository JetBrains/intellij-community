class C {
    void method() {
        method(27);
    }

    void method(int i) {
    }
}

class C1 extends C {
    void method(int i) {
    }
}

class Usage {
    {
        new C().method();
        new C1().method();
    }
}