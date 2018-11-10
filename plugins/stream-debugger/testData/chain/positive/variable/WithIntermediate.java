import java.util.stream.IntStream;

public class WithIntermediate {
    public static void main(String[] args) {
        IntStream stream = IntStream.of(1, 2, 3);
        <caret>stream.filter(x -> x % 2 == 1).sum();
    }
}