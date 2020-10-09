import lombok.ToString;

public class Parentclass {

  private String stringProperty;

  public String getStringProperty() {
    return stringProperty;
  }

  public String toString() {
    return "Parentclass(stringProperty=" + this.getStringProperty() + ")";
  }

}

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

  public String toString() {
    return "SomeTestWithSuper(super=" + super.toString() + ", intProperty=" + this.getIntProperty() + ", booleanProperty=" + this.isBooleanProperty() + ", doubleProperty=" + this.getDoubleProperty() + ")";
  }

}
