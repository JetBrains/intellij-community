import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(prefix = "m", fluent = true)
public class FindUsageAccessors {
  private int mFoo;

  @Accessors(prefix = "_", fluent = false)
  private String _<caret>Bar;

  private String bMar;

  public static void main(String[] args) {
    FindUsageAccessors findUsageAccessors = new FindUsageAccessors();
    findUsageAccessors.setBar("myBar");
    findUsageAccessors.foo(1981);
    System.out.println("Bar is: " + findUsageAccessors.getBar());
    System.out.println("Foo is: " + findUsageAccessors.foo());
  }
}
