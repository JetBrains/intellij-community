public class Test {
    void testRequire(String s1, boolean b1, boolean b2) {
        if (s1 == null) {
            throw new IllegalArgumentException("s should not be null");
        }

        if (!b1) {
            throw new IllegalArgumentException();
        } else {
            System.out.println("never mind");
        }
        // comment above b2
        if (b2) {
            throw new IllegalArgumentException();
        }

        if (!b1 && b2) throw new IllegalArgumentException();
        else {
            System.out.println(1);
            System.out.println(2);
        }

        if (s1.length() < 3) {
            throw new IllegalArgumentException();
        } else if (s1.length() == 4) {
            System.out.println(1);
        } else {
            System.out.println(2);
        }
    }

    void testCheck(boolean b, String notNullString) {
        if (b) throw new IllegalStateException();

        // comment above notNullString
        if (notNullString == null) {
            throw new IllegalStateException()
        }
    }

    void testDoubles(double x, double y) {
        if (!(x < y)) {
            throw new IllegalStateException()
        }
        if (y < 2*x) {
            throw new IllegalStateException()
        }
    }

    void doNotTouch(boolean b, String s1) {
        try {
            System.out.println("hello!");
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw new IllegalStateException(e);
            }
        }

        if (b) {
            throw new IndexOutOfBoundsException();
        }

        if (s1.length() < 5) {
            System.out.println("Some other side effect");
            throw new IllegalStateException("oops");
        }
    }
}