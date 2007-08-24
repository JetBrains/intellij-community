public class Bar {
    public int baz(byte blah) {
        return baz(blah + blah + 3);
    }

    public int baz(int anObject) {
        return <caret>anObject;
    }
}
