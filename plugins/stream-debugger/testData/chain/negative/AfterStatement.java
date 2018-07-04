import java.util.stream.Stream;

public class Bar {
  public static void main(String[] args) {
    int before = 10;
    final long count = Stream.of("abc", "acd", "ef").map(String::length).count();<caret>
    int after = 20;
  }
}
