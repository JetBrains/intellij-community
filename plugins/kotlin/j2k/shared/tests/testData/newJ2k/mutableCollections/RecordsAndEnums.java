import java.util.*;

record Names(List<String> values) {
    int count() {
        return values.size();
    }
}

enum Color {
    RED(List.of("r"));

    private final List<String> names;

    Color(List<String> names) {
        this.names = names;
    }

    int count() {
        return names.size();
    }
}
