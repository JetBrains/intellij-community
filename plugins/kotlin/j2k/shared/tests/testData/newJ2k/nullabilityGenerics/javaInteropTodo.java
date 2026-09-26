import java.util.ArrayList;

public class Foo {
    void testAssignment(J j) {
        ArrayList<String> l1 = j.return1();
        ArrayList<String> l2 = j.return2();
        ArrayList<String> l3 = j.return3();
        ArrayList<String> l4 = j.return4();

        ArrayList<String> l5 = j.field1;
        ArrayList<String> l6 = j.field2;
        ArrayList<String> l7 = j.field3;
        ArrayList<String> l8 = j.field4;
    }

    void testArgument(
            J j,
            ArrayList<String> l1,
            ArrayList<String> l2,
            ArrayList<String> l3,
            ArrayList<String> l4
    ) {
        j.argument1(l1);
        j.argument2(l2);
        j.argument3(l3);
        j.argument4(l4);
    }

    ArrayList<String> testReturn1(J j) {
        return j.return1();
    }

    ArrayList<String> testReturn2(J j) {
        return j.return2();
    }

    ArrayList<String> testReturn3(J j) {
        return j.return3();
    }

    ArrayList<String> testReturn4(J j) {
        return j.return4();
    }
}
