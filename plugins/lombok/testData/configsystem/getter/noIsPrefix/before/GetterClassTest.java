import lombok.Getter;

@Getter
public class GetterClassTest {
  private int intProperty;
  private String stringProperty;
  private boolean booleanProperty;
  private Boolean booleanObjectProperty;

  public static void main(String[] args) {
    final GetterClassTest test = new GetterClassTest();

    test.getIntProperty();
    test.getStringProperty();
    test.getBooleanObjectProperty();

    test.getBooleanProperty();
  }
}