import java.util.ArrayList;

public class A {
    void foo(ArrayList<String> collection) {
        for(int i = collection.size() - 1; i >= 0; i--) {
            System.out.println(i);
        }
    }
}
