import java.util.*;

class J {
    private final ArrayList<String> strings = new ArrayList<>();

    public void report(String s) {
        strings.add(s);
    }

    public ArrayList<String> returnStrings() {
        return strings; // update return expression type from method return type
    }

    public void test() {
        for (String s : returnStrings()) {
            System.out.println(s.length());
        }
    }
}
