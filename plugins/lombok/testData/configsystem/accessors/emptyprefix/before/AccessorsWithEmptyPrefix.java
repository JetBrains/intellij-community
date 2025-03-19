import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors
public class AccessorsWithEmptyPrefix {
  private int _5gSomething;
  private double anyField;

  public static void main(String[] args) {
    final GetterSetterClassTest test = new GetterSetterClassTest();
    test.get5gSomething();
    test.getAnyField();

    System.out.println(test);
  }
}