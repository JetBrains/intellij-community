// TODO investigate why extension methods are unresolved in this test (imports are not added)
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
}
