import java.util.function.BiFunction;

public class Test {
    void notNullParameters(BiFunction<Integer, Integer, String> f) {
    }

    void nullableParameter(BiFunction<Integer, Integer, String> f) {
    }

    void nullableReturnType(BiFunction<Integer, Integer, String> f) {
    }

    void test() {
        notNullParameters((i, j) -> i < 10 && j > 0 ? "" : "");
        nullableParameter((i, j) -> i == null ? "" : "");
        nullableReturnType((i, j) -> {
            if (i < 10) return null;
            return "nya";
        });
    }
}