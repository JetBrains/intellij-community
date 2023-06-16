// RUNTIME_WITH_FULL_JDK
public class J {
    public void foo(boolean bool, char c, byte b, short s, int i, long l, float f, double d) {
        Boolean.toString(bool);
        Character.toString(c);
        Character.toString(i);
        Byte.toString(b);
        Short.toString(s);
        Integer.toString(i);
        Integer.toString(i, i);
        Integer.toString(i, 1);
        Integer.toString(i, 8);
        Integer.toString(i, 42.0); // invalid code
        Long.toString(l);
        Long.toString(l, i);
        Long.toString(l, 1);
        Long.toString(l, 8);
        Long.toString(l, 42.0); // invalid code
        Float.toString(f);
        Double.toString(d);
    }
}