package de.plushnikov.val;

import lombok.val;

public class ValInspection {

    private val field;

    public void test(val x) {
    }

    public void test() {
        val a = 1;

        val b = "a2";

        val e = System.getProperty("sss");

        val c = new int[]{1};

        for (val i = 0; i < 10; i++) {
            val j = 2;
        }
    }
}
