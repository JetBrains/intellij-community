import lombok.Getter;
import lombok.experimental.Delegate;

public class FindUsageDelegateField {
  public static void main(String[] args) {
    Ctx<Integer> ctx = new Ctx<>();
    String data = ctx.getId();
    System.out.println(data);
  }

  public static class Ctx<T> {
    @Delegate
    private Param<T> param;
  }

  @Getter
  public static class Param<T> {
    private final String id<caret>;
  }
}