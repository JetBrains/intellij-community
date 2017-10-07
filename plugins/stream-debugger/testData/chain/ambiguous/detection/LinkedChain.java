import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LinkedChain {
  public static void main(String[] args) {
    final long cou<caret>nt = Stream
      .of(1, 2, 3).distinct().collect(Collectors.toList())
      .stream().sorted().collect(Collectors.toList())
      .stream().count();
  }
}
