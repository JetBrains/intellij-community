import java.util.stream.Stream;

public class InAnonymous {
  public static void main(String[] args) {
<caret>    Runnable a = new Runnable() {
      @Override
      public void run() {
        long c = Stream.of(1,2).count();
      }
    };
  }
}
