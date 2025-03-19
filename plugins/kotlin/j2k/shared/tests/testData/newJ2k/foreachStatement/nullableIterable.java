import java.util.ArrayList;

class A {
    ArrayList<String> list = null;

    void foo() {
        for (String e : list) {
            System.out.println(e);
        }
    }
}