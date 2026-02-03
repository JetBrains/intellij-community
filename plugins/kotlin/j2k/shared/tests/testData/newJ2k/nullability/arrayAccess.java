public class Foo {
    private void test(int[] q, int[] q2, Integer i) {
        System.out.println(q[0]);
        System.out.println(q[i]);
        q2[0] = 42;
    }

    private void test2(int[] q, int[] q2, Integer i) {
        for (int j = 10; j >= 0; j--) {
            // empty loop that revealed a bug in nullity inferrer for some reason (?)
        }

        System.out.println(q[0]);
        System.out.println(q[i]);
        q2[0] = 42;
    }
}