import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

class MethodCallArgument {
    void test() {
        takesStrings(strings(), strings());
    }

    ArrayList<@NotNull String> strings() {
        return new ArrayList<>();
    }

    private void takesStrings(ArrayList<String> strings1, ArrayList<String> strings2) {
    }
}

class MethodCallArgumentReverse {
    void test() {
        takesStrings(strings1(), strings2());
    }

    ArrayList<String> strings1() {
        return new ArrayList<>();
    }
    ArrayList<String> strings2() {
        return new ArrayList<>();
    }

    private void takesStrings(ArrayList<@NotNull String> strings1, ArrayList<@NotNull String> strings2) {
    }
}
