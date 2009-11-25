/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Nov 15, 2004
 * Time: 5:40:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    Long[] bar() {
        return new Long[0];
    }

    Long[][] foo(int n, int k) {
        Long[][] a = new Long[][]{new Long[0], new Long[0]};

        for (int i = 0; i < a.length; i++) {
            a[i] = bar();
        }

        return a;
    }
}
