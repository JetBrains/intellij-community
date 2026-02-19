import java.util.ArrayList;

public class Foo {
    void test() {
        ArrayList<String> nullableList = J.nullableList();
        ArrayList<String> notNullList = J.notNullList();

        ArrayList<String> unknownListNullable = J.unknownList();
        ArrayList<String> unknownListNotNull = J.unknownList();

        ArrayList<String> unrelatedListNotNull = J.unrelatedList();
        ArrayList<String> unrelatedList2 = J.unrelatedList();

        for (String s : notNullList) {
            System.out.println(s.length());
        }

        for (String s : unknownListNotNull) {
            System.out.println(s.length());
        }

        for (String s : unknownListNullable) {
            if (s != null) {
                System.out.println(s.length());
            }
        }

        for (String s : unrelatedListNotNull) {
            System.out.println(s.length());
        }
    }
}
