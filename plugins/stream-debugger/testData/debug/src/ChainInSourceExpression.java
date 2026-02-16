import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChainInSourceExpression {
  public static void main(String[] args) {
    // Breakpoint!
    final String res = makeProducer()
      .map(len -> "Length is " + len)
      .collect(Collectors.joining());
  }

  public static Stream<Integer> makeProducer() {
    return Stream
      .of(countLetters("foo"), countLetters("bar"), countLetters("baz"));
  }

  public static int countLetters(String input) {
    return Arrays
      .stream(input.split(""))
      .map(str -> str)
      .mapToInt(str -> str.length())
      .sum();
  }
}