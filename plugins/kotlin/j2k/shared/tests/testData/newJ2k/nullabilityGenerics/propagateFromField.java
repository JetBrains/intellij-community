import java.util.*;

class J {
    public ArrayList<String> stringsField = new ArrayList<>();

    public void test() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    private ArrayList<String> returnStrings() {
        return stringsField;
    }
}
