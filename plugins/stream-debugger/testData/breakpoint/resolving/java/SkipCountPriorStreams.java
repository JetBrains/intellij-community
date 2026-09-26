import java.util.Arrays;
import java.util.List;

public class SkipCountPriorStreams {
  public static void main(String[] args) {
    List<Integer> list = Arrays.asList(1, 2, 3);
    list.stream().count();
    list.stream().count();
<caret>    list.stream().map(x -> x * 2).toList();
  }
}
