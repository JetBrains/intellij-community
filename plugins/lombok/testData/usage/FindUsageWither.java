import lombok.Value;
import lombok.experimental.Wither;

@Wither
@Value
public class FindUsageWither {
  private int foo;
  private String b<caret>ar;

  public static void main(String[] args) {
    FindUsageWither findUsageWither = new FindUsageWither(1, "bar");
    findUsageWither
      .withBar("myBar")
      .withFoo(1981);
    System.out.println("Bar is: " + findUsageWither.getBar());
    System.out.println("Foo is: " + findUsageWither.getFoo());
  }
}
