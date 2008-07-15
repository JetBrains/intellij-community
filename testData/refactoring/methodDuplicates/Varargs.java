import java.util.*;
public class Varargs {
    private List<String> cr<caret>eateSet(String... values) {
        return Arrays.asList(values);
    }

    private List<String> method() {
        return Arrays.asList("hi", "bye");
    }
}