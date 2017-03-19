import java.util.stream.Stream;

public class ForEachOrdered {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(1,2,3,4).forEachOrdered(System.out::println);
  }
}
