import java.util.stream.Stream;

public class WeirdFormatting {
  public static void main(String[] args) {
<caret>    Stream.
      of(1, 2, 3).
      map(x -> x * 2).
      toList();
  }
}
