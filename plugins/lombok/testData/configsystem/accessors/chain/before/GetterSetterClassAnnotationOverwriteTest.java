import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = false)
public class GetterSetterClassAnnotationOverwriteTest {
  private int intProperty;
  private double doubleProperty;
  private boolean booleanProperty;
  private String stringProperty;

  public static void main(String[] args) {
    final GetterSetterClassAnnotationOverwriteTest test = new GetterSetterClassAnnotationOverwriteTest();
    test.setStringProperty("");
    test.setIntProperty(1)
    test.setBooleanProperty(true)
    test.setDoubleProperty(0.0);

    System.out.println(test);
  }
}