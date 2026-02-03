import java.util.stream.Stream;

public class InLambda {
  public static void main(String[] args) {
<caret>    Runnable a = () -> {
      Stream.of(1,2).count();
    };
  }
}
