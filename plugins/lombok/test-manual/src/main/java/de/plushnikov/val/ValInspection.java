package de.plushnikov.val;

import lombok.val;

import java.util.Arrays;

public class ValInspection {

    private val field;

//    public void test(val x) {
//    }

    public void test(int x, float y) {
    }

    public void test() {
        val a = 1;

        val b = "a2";

        val c = new int[]{1};

        val d = System.getProperty("sss");

        // 'val' is not allowed in old-style for loops
//        for (val i = 0; i < 10; i++) {
//            val j = 2;
//        }
        val xxx = "s";
        // 'val' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })
//        val e = {"xyz"};

        // 'val' is not allowed with lambda expressions.
//        val f = () -> "xyz";

        // 'val' on a local variable requires an initializer expression
//        val g;

        for (val h : Arrays.asList("a", "b", "c")) {
            System.out.println(h);
        }

        String val = "val";
        System.out.println(val);
    }
}
