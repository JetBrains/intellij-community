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
    test.setStringProperty("")
        .setIntProperty(1)
        .setBooleanProperty(true)
        .setDoubleProperty(0.0);

    System.out.println(test);
  }
}