public class J {
    void foo(double x, double y, float f) {
        Math.abs(x);
        Math.acos(x);
        Math.asin(x);
        Math.atan(x);
        Math.atan2(x, y);
        Math.cbrt(x);
        Math.ceil(x);
        Math.copySign(x, y);
        Math.cos(x);
        Math.cosh(x);
        Math.exp(x);
        Math.expm1(x);
        Math.floor(x);
        Math.hypot(x, y);
        Math.IEEEremainder(x, y);
        Math.log(x);
        Math.log1p(x);
        Math.log10(x);
        Math.max(x, y);
        Math.min(x, y);

        // Don't convert `Math.nextAfter`, because Kotlin's `nextTowards` overloads have slightly different signatures
        Math.nextAfter(x, y);

        Math.nextDown(x);
        Math.nextUp(x);
        Math.pow(x, y);
        Math.rint(x);
        // Don't convert `Math.round` calls, because Kotlin's `round` throws an exception for a NaN argument
        Math.round(x);
        Math.round(f);
        Math.signum(x);
        Math.sin(x);
        Math.sinh(x);
        Math.sqrt(x);
        Math.tan(x);
        Math.tanh(x);
    }
    
    void overloads(short s, byte b, int i, long l, float f, double d) {
        Math.abs(s);
        Math.abs(b);
        Math.abs(i);
        Math.abs(l);
        Math.abs(f);
        Math.abs(d);
        Math.abs(1);
        Math.abs(1.0);

        Math.max(s, i);
        Math.max(b, i);
        Math.max(i, i);
        Math.max(i, l);
        Math.max(i, f);
        Math.max(i, d);
        Math.max(1, 2);
        Math.max(1, 2.0);

        Math.min(s, i);
        Math.min(b, i);
        Math.min(i, i);
        Math.min(i, l);
        Math.min(i, f);
        Math.min(i, d);
        Math.min(1, 2);
        Math.min(1, 2.0);
    }

    void floatingPointOverloads(int i, float f, double d) {
        Math.copySign(i, f);
        Math.copySign(f, f);
        Math.copySign(i, d);
        Math.copySign(d, d);
        Math.copySign(1.0F, f);
        Math.copySign(1.0, 2.0);

        // Don't convert `Math.nextAfter`, because Kotlin's `nextTowards` overloads have slightly different signatures
        Math.nextAfter(i, f);
        Math.nextAfter(f, f);
        Math.nextAfter(i, d);
        Math.nextAfter(d, d);
        Math.nextAfter(1.0F, f);
        Math.nextAfter(1.0, 2.0);

        Math.nextDown(i);
        Math.nextDown(f);
        Math.nextDown(d);
        Math.nextDown(1);
        Math.nextDown(1.0F);
        Math.nextDown(1.0);

        Math.nextUp(i);
        Math.nextUp(f);
        Math.nextUp(d);
        Math.nextUp(1);
        Math.nextUp(1.0F);
        Math.nextUp(1.0);

        Math.signum(i);
        Math.signum(f);
        Math.signum(d);
        Math.signum(1);
        Math.signum(1.0F);
        Math.signum(1.0);
    }
}
