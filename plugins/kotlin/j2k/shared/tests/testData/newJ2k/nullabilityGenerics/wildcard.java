import java.util.*;

class TestWildcard {
    public ArrayList<? extends Number> extendsNumber = new ArrayList<>();
    public ArrayList<? super Number> superNumber = new ArrayList<>();

    public void test() {
        for (Number n : extendsNumber) {
            System.out.println(n.hashCode());
        }
        for (Object o : superNumber) {
            System.out.println(o.hashCode());
        }
    }
}
