// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// IGNORE_K2
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {

    public static final String TEXT1 = "text1.\n" +
                                       "text2\n" +
                                       "text3";
    void foo(int a, int b, string s1) {
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;
        int e = 0;

        a = b += (c) = ((d *= e));
        a = (((b)));
        a += (b);
        a += b = (c);
        a = ((b) += (c));

        char a = (byte) 1;

        List<String> x = new ArrayList<String>();
        CollectionsKt.filter(x, new Function1<String, Boolean>() {
            @Override
            public Boolean invoke(String o) {
                return o.equals("a");
            }
        });

        if (!(0 != 1) && a > b) return;
        if (0 == 1 && !/*comment 1*/(/*comment 2*/a == b)) return;

        if (s1.length() < 3) {
            throw new IllegalArgumentException();
        }

        bar(s1 + "hello");

        s1.length();
        (s1 + "postfix").length();

        int z =
                1 +
                2 +
                3;

        z =
                1 +
                2 +
                3;

        System.out.println(1 << 2 | 3);
        System.out.println(false | true & 5 == 5 >>> 7);
        int insideMultiline = 1
                              + 1
                              - 0x1234 & 0x1234 >>> 1;
    }

    @Nullable
    Object bar(String s) {
        System.out.println("s = " + s);
        // TODO: add nested `x ? y : z`
        return s == null ? "" : null;
    }

    @NotNull
    Object bar() {
        return bar(null);
    }
}