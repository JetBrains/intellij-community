import java.util.ArrayList;

public class Example {
    public void test(ArrayList<Object> objects) {
        objects.forEach(o -> {
            if (o instanceof String s) {
                System.out.println(s);
            }
        });
    }
}