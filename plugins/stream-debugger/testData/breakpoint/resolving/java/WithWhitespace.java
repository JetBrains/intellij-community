import java.util.stream.Stream;

public class WithWhitespace {
  public static void main(String[] args) {
<caret>    Stream // comment
      .of(1, 2, 3)
      .map(x -> x * 2) /* inline */
      .toList();
  }
}
