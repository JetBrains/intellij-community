class C {
    void test1(Object obj) {
        if (obj instanceof String) {
            System.out.println("String" + obj.hashCode()); // give up
        }
    }

    void test2(Object obj) {
        if (obj instanceof String) {
            System.out.println("String" + obj.hashCode()); // give up
        } else {
            System.out.println("Object" + obj.hashCode()); // not-null
        }
    }

    void test3(Object obj) {
        if (!(obj instanceof String)) {
            System.out.println("Not String" + obj.hashCode()); // not-null
        }
    }

    void test4(Object obj) {
        if (!(obj instanceof String)) {
            System.out.println("Not String");
        } else {
            System.out.println("String" + obj.hashCode()); // give up (like in test1)
        }
    }

    void test5(Object obj) {
        if (obj instanceof String) {
            System.out.println("String" + obj.hashCode()); // give up
        }
        System.out.println("Object" + obj.hashCode()); // not-null
    }

    void test6(Object obj, boolean b) {
        if (b || obj instanceof String) {
            System.out.println(obj.hashCode()); // not-null
        } else {
            System.out.println(obj.hashCode()); // not-null
        }
    }

    void test7(Object obj, boolean b) {
        if (b && obj instanceof String) {
            System.out.println(obj.hashCode()); // give up
        } else {
            System.out.println(obj.hashCode()); // not-null
        }
    }
}