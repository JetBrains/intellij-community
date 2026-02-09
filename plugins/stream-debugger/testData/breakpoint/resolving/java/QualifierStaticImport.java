import static java.util.stream.Stream.of;

public class QualifierStaticImport {
  public static void main(String[] args) {
<caret>    of(1, 2, 3)
      .map(x -> x * 2)
      .toList();
  }
}
