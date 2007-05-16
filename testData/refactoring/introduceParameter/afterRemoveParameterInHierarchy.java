public class Bar {
    public int baz(int anObject) {
        return anObject;
    }
}
class S extends Bar {
    public int baz(int anObject) {
        return super.baz((byte) 0 + 3);    //To change body of overridden methods use File | Settings | File Templates.
    }
}