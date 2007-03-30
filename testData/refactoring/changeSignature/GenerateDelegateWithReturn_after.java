class C {
    String method() {
        return method(27);
    }

    String <caret>method(int i) {
    }
}

class C1 extends C {
    String method(int i) {
    }
}