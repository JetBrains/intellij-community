import lombok.ToString;

@ToString
public class Parentclass {

  private String stringProperty;

  public String getStringProperty() {
    return stringProperty;
  }

}

@ToString
public class SomeTestWithSuper extends Parentclass {
  
  private int intProperty;
  private boolean booleanProperty;
  private double doubleProperty;

  public int getIntProperty() {
    return intProperty;
  }

  public boolean isBooleanProperty() {
    return booleanProperty;
  }

  public double getDoubleProperty() {
    return doubleProperty;
  }

  public static void main(String[] args) {
    final SomeTest test = new SomeTest();
    System.out.println(test.hashCode());
  }
}
