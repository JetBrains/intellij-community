public class CompanionCallNameConflicts {
    static int twice(int x) {
        return inc(inc(x));
    }

    static int inc(int x) {
        return x + 1;
    }

    int callFromClass(int x) {
        return twice(x);
    }

    void log(Object o) {
    }

    static void log(int i) {
    }

    void callOverloadedByInstanceMethod() {
        log(1);
    }

    static class Nested {
        int callFromNested() {
            return twice(1);
        }
    }

    <Int> Int typeParameterNamedInt(Int value, int count) {
        return value;
    }
}
