import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindUsageGetterSetter {
  private int foo;
  private String b<caret>ar;

  public static void main(String[] args) {
    FindUsageGetterSetter findUsageGetterSetter = new FindUsageGetterSetter();
    findUsageGetterSetter.setBar("myBar");
    findUsageGetterSetter.setFoo(1981);
    System.out.println("Bar is: " + findUsageGetterSetter.getBar());
    System.out.println("Foo is: " + findUsageGetterSetter.getFoo());
  }
}
