import java.util.stream.IntStream;

public class TerminationOnly {
    public static void main(String[] args) {
        IntStream stream = IntStream.of(1, 2, 3);
        <caret>stream.sum();
    }
}