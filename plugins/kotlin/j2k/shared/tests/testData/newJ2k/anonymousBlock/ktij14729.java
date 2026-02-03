import java.util.ArrayList;
import java.util.List;

public class Temp1 {
    private List<Integer> listField;

    {
        listField = new ArrayList<>();
    }

    void m() {
        listField.add(2);
        listField.add(1);
        for (Integer i : listField) {
            System.out.println(i);
        }
    }
}