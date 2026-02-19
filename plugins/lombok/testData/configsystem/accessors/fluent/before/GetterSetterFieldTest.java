import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class GetterSetterFieldTest {
  @Accessors
  private int intProperty;
  @Accessors
  private double doubleProperty;
  @Accessors
  private boolean booleanProperty;
  @Accessors
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterFieldTest test = new GetterSetterFieldTest();
    test.stringProperty("");
    test.intProperty(1);
    test.booleanProperty(true);
    test.doubleProperty(0.0);

    System.out.println(test);
  }
}