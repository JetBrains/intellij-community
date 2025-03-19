import java.util.ArrayList;

public class Main {
    public ArrayList<String> f1() {
        return new ArrayList<>();
    }

    public ArrayList<String> f2() {
        ArrayList<String> list = f1();
        f3(list);
        return list;
    }

    public void f3(ArrayList<String> list) {
        for (String item : list) {
            System.out.println(item.length());
        }
    }
}