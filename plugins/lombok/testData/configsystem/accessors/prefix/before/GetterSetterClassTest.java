import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors
public class GetterSetterClassTest {
  private int mIntProperty;
  private double pDoubleProperty;
  private boolean m_BooleanProperty;
  private String aStringProperty;

  public static void main(String[] args) {
    final GetterSetterClassTest test = new GetterSetterClassTest();
    test.getIntProperty();
    test.setIntProperty(1);
    test.getDoubleProperty();
    test.setDoubleProperty(0.0);
    test.isBooleanProperty();
    test.setBooleanProperty(true);
    test.getAStringProperty();
    test.setAStringProperty("");

    System.out.println(test);
  }
}