import java.util.stream.Stream;

public class ForEachBreakpointBased {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1,2,3,4).forEach(System.out::println);
  }
}
