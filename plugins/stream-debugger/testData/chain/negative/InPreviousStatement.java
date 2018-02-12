import java.util.stream.Stream;

public class Bar {
  public static void main(String[] args) {
    int before = 1<caret>0;
    final long count = Stream.of("abc", "acd", "ef").map(String::length).count();
  }
}
