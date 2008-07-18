public class A {
    private B myField;

    public void method() {
        String a = myField.bbb.xxx();
    }

    public B get<caret>Field() {
        return myField;
    }

    private static class B {
        private C bbb;
    }

    private static class C {
        String xxx() {return null;}
    }
}
