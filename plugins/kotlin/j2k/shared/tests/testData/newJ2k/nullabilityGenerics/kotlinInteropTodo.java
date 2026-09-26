import java.util.ArrayList;

public class Foo {
    void testAssignment(K k) {
        ArrayList<String> l1 = k.return1();
        ArrayList<String> l2 = k.return2();
        ArrayList<String> l3 = k.return3();
        ArrayList<String> l4 = k.return4();
    }

    void testArgument(
            K k,
            ArrayList<String> l1,
            ArrayList<String> l2,
            ArrayList<String> l3,
            ArrayList<String> l4
    ) {
        k.argument1(l1);
        k.argument2(l2);
        k.argument3(l3);
        k.argument4(l4);
    }

    ArrayList<String> testReturn1(K k) {
        return k.return1();
    }

    ArrayList<String> testReturn2(K k) {
        return k.return2();
    }

    ArrayList<String> testReturn3(K k) {
        return k.return3();
    }

    ArrayList<String> testReturn4(K k) {
        return k.return4();
    }
}
