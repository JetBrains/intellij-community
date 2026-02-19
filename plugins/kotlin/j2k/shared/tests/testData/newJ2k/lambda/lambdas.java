// IGNORE_K2

import java.util.*;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public class Java8Class {
    public void foo0(Function0<String> r) {
    }

    public void foo1(Function1<Integer, String> r) {
    }

    public void foo2(Function2<Integer, Integer, String> r) {
    }

    public void foo3(Function0<String> r1, Function0<String> r2) {
    }

    public void bar(Function0<String> f) {
        // It seems we can't resolve overloaded methods, so this lambda stays inside the parentheses
        bar(f, (i) -> "default g");
    }

    public void bar(Function0<String> f, Function1<Integer, String> g) {
    }

    public doNotTouch(Function0<String> f, String s) {
    }

    public void helper() {
    }

    public void vararg(String key, Function0<String>... functions) {
    }

    public static void runnableFun(Runnable r) {}

    class Base {
        Base(String name, Function0<String> f) {
        }
    }

    class Child extends Base {
        Child() {
            super("Child", () -> {
                return "a child class";
            })
        }
    }

    public void foo() {
        foo0(() -> "42");
        foo0(() -> { return "42"; });
        foo0(() -> {
            helper();
            return "42";
        });

        foo1((i) -> "42");
        foo1(i -> { return "42"; });
        foo1((Integer i) -> {
            helper();
            if (i > 1) {
                return null;
            }

            return "43";
        });

        foo2((i, j) -> "42");
        foo2((Integer i, Integer j) -> {
            helper();
            return "42";
        });

        foo3(() -> "42", () -> "42");

        bar(() -> "f", (i) -> "g");

        assert "s" != null : "that's strange";

        Base base = new Base("Base", () -> "base");

        vararg("first", () -> "f");

        runnableFun(new Runnable() {
            @Override
            public void run() {
                "hello"
            }
        });

        Function2<Integer, Integer, String> f = (Integer i, Integer k) -> {
            helper();
            if (i > 1) {
                return "42";
            }

            return "43";
        };

        Function2<Integer, Integer, String> f1 = (Integer i1, Integer k1) -> {
            Function2<Integer, Integer, String> f2 = (Integer i2, Integer k2) -> {
                helper();
                if (i2 > 1) {
                    return "42";
                }

                return "43";
            };
            if (i1 > 1) {
                return f.invoke(i1, k1);
            }
            return f.invoke(i1, k1);
        };

        Runnable runnable1 = () -> { };

        Runnable runnable2 = () -> {
            if (true) return;
            System.out.println("false");
        };

        foo1((Integer i) -> {
            if (i > 1) {
                return "42";
            }

            foo0(() -> {
                if (true) {
                    return "42";
                }
                return "43";
            });

            return "43";
        });

        doNotTouch(() -> {
            return "first arg";
        }, "last arg");
    }

    void moreTests(
            Map<String, String> m1,
            Map<String, String> m2,
            Map<String, String> m3,
            Map<String, String> m4
    ) {
        m1.compute("m1", (k, v) -> v);
        m2.computeIfAbsent("m2", (k) -> "value");
        m3.computeIfPresent("m1", (k, v) -> v);
        m4.merge("", "", (k, v) -> v);
        m4.merge("", "", String::concat);

        String [][] ss = new String[5][5];

        String s = "test";
        s.trim();
    }
}