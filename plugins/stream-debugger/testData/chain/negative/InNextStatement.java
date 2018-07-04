import java.util.stream.Stream;

public class Bar {
  public static void main(String[] args) {
    final long count = Stream.of("abc", "acd", "ef").map(String::length).count();
    int a<caret>fter = 20;
  }
}
