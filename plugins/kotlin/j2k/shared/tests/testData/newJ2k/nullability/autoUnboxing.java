class C {
    void testAssignment(Integer i1, Integer i2, Integer i3, Integer i4, Integer i5) {
        int j = i1;
        j += i2;
        j >>= i3;
        i4 += j;
        i5 = j; // not applicable
    }

    void testUnaryExpr(Integer i1, Integer i2, Integer i3, Integer i4) {
        System.out.println(++i1);
        System.out.println(i2++);
        System.out.println(--i3);
        System.out.println(i4--);
    }

    void testBinaryExpr(Integer i1, Integer i2, Integer i3, Integer i4, Integer i5) {
        System.out.println(i1 + 1);
        System.out.println(1 - i2);
        System.out.println(i3 * 1);
        System.out.println(1 / i4);
        System.out.println(i5 % 1);
    }

    void testNumberComparison(Integer i1, Integer i2, Integer i3, Integer i4, Integer i5) {
        System.out.println(i1 == 1);
        System.out.println(1 != i2);
        System.out.println(i3 > 1);
        System.out.println(1 <= i4);
        System.out.println(i5 == i1); // not applicable
    }

    void testBooleanComparison(Boolean b1, Boolean b2, Boolean b3, Boolean b4, boolean b5) {
        System.out.println(b1 == true);
        System.out.println(false == b2);
        System.out.println(b3 == b4); // not applicable
        System.out.println(b3 != null); // not applicable
        System.out.println(b4 == b5);
    }

    void testLogical(Boolean b1, Boolean b2, Boolean b3, boolean b4) {
        System.out.println(b1 && b4);
        System.out.println(b4 || b2);
        System.out.println(!b3);
    }

    void testConditionals(Boolean b1, Boolean b2, Boolean b3, Boolean b4) {
        if (b1) {
        }
        while (b2) {
        }
        for (; b3; ) {
        }
        System.out.println(b4 ? "yes" : "no");
    }

    void testMethodCall(Integer i) {
        takesPrimitiveInt(i);
    }

    // Not applicable for Strings
    void testStrings(String s1, int i) {
        System.out.println(s1 + i);
    }

    void takesPrimitiveInt(int i) {
    }
}