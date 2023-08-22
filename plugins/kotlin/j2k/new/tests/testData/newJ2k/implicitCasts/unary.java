public class J {
    public void foo(byte b, char c, short s, int i, long l, float f, double d) {
        i(-b);
        i(+b);
        i(~b);

        i(-c);
        i(+c);
        i(~c);

        i(-s);
        i(+s);
        i(~s);

        i(-i);
        i(+i);
        i(~i);

        l(-l);
        l(+l);
        l(~l);

        f(-f);
        f(+f);

        d(-d);
        d(+d);
    }

    public void i(int i) {}
    public void l(long l) {}
    public void f(float f) {}
    public void d(double d) {}
}