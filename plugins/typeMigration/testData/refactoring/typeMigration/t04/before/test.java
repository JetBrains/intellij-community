/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Nov 15, 2004
 * Time: 5:40:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    Integer[] bar() {
        return new Integer[0];
    }

    Integer[] foo(int n, int k) {
        Integer[][] a = new Integer[][] {new Integer[0], new Integer[0]};

        for (int i = 0; i < a.length; i++) {
            a[i] = bar();
        }

        return a;
    }
}
