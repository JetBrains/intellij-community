import lombok.Getter;

public class GetterFieldTest {
  @Getter
  private int intProperty;

  @Getter
  private String stringProperty;

  @Getter
  private boolean booleanProperty;

  @Getter
  private Boolean booleanObjectProperty;

  public static void main(String[] args) {
    final GetterFieldTest test = new GetterFieldTest();

    test.getIntProperty();
    test.getStringProperty();
    test.getBooleanObjectProperty();

    test.getBooleanProperty();
  }
}