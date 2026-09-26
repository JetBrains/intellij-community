import java.util.ArrayList;

public class J {
    private void test(ArrayList<String> strings) {
        ArrayList<String> strings1 = strings;
        ArrayList<String> strings2 = strings1;

        for (String s : strings2) {
            System.out.println(s.hashCode());
        }
    }
}