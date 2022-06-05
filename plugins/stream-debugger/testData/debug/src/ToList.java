import java.util.List;
import java.util.stream.Stream;

public class ToList {
  public static void main(String[] args) {
    // Breakpoint!
    final List<Object> objects = Stream.of(new Object(), new Object(), new Object()).toList();
  }
}