import java.util.stream.Stream;

public class StopInsideBlockLambda {
  public static void main(String[] args) {
    Stream.of(5, 6).filter(x -> {
      // Breakpoint!
      if (x % 2 == 0) {
        return true;
      }
      return false;
    }).toList();
  }
}
