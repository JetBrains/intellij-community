import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class GetterSetterFieldAnnotationOverwriteTest {
  @Accessors(chain=false)
  private int intProperty;
  @Accessors(chain=false)
  private double doubleProperty;
  @Accessors(chain=false)
  private boolean booleanProperty;
  @Accessors(chain=false)
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterFieldAnnotationOverwriteTest test = new GetterSetterFieldAnnotationOverwriteTest();
    test.setStringProperty("");
    test.setIntProperty(1)
    test.setBooleanProperty(true)
    test.setDoubleProperty(0.0);

    System.out.println(test);
  }
}