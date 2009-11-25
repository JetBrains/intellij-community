
/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Oct 16, 2004
 * Time: 10:10:35 PM
 * To change this template use File | Settings | File Templates.
 */

class Foo {
    Moo moo(int i) {
        return null;
    }
}

class Moo {
    Foo foo(Integer[] j) {
        return null;
    }
}

class P {
    int f(int y) {
        return y;
    }
}

class G extends P {
    int f(int y) {
        return y;
    }
}

public class Test {
    Moo g(Foo i) {
        Foo j = i.moo(new Integer[0]);
        return null;
    }
}
