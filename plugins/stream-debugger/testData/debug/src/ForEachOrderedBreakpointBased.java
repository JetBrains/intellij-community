import java.util.stream.Stream;

public class ForEachOrderedBreakpointBased {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1,2,3,4).forEachOrdered(System.out::println);
  }
}
