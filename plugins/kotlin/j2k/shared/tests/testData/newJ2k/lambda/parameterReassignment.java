import java.util.ArrayList;

public class J {
    void foo(ArrayList<Integer> numbers) {
        numbers.forEach((n) -> n = n + 1);
        numbers.forEach((n) -> {
            n = n + 1;
        });
    }
}