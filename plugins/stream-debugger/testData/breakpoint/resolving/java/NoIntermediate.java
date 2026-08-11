import java.util.stream.Stream;

public class NoIntermediate {
  public static void main(String[] args) {
<caret>    Stream.of(1, 2, 3).toList();
  }
}
