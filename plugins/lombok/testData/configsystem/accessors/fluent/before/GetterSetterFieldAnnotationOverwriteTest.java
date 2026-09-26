import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
public class GetterSetterFieldAnnotationOverwriteTest {
  @Accessors(fluent=false)
  private int intProperty;
  @Accessors(fluent=false)
  private double doubleProperty;
  @Accessors(fluent=false)
  private boolean booleanProperty;
  @Accessors(fluent=false)
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