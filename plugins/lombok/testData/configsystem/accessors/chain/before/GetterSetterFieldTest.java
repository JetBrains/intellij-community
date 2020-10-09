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
    test.setStringProperty("")
        .setIntProperty(1)
        .setBooleanProperty(true)
        .setDoubleProperty(0.0);

    System.out.println(test);
  }
}