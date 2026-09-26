import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors
public class GetterSetterClassTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterClassTest test = new GetterSetterClassTest();
    test.stringProperty("");
    test.intProperty(1);
    test.booleanProperty(true);
    test.doubleProperty(0.0);

    System.out.println(test);
  }
}