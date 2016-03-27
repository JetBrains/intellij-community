public class WithSuperTest {

  private static class BasisClass {
    private int intProperty;
    private boolean booleanProperty;

    public int getIntProperty() {
      return intProperty;
    }

    public boolean isBooleanProperty() {
      return booleanProperty;
    }

    public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof BasisClass)) return false;
      final BasisClass other = (BasisClass) o;
      if (!other.canEqual((java.lang.Object) this)) return false;
      if (!super.equals(o)) return false;
      if (this.getIntProperty() != other.getIntProperty()) return false;
      if (this.isBooleanProperty() != other.isBooleanProperty()) return false;
      return true;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      result = result * PRIME + super.hashCode();
      result = result * PRIME + this.getIntProperty();
      result = result * PRIME + (this.isBooleanProperty() ? 79 : 97);
      return result;
    }

    protected boolean canEqual(Object other) {
      return other instanceof BasisClass;
    }
  }

  private static class ExtendedClass extends BasisClass {
    private double doubleProperty;
    private String stringProperty;

    public double getDoubleProperty() {
      return doubleProperty;
    }

    public String getStringProperty() {
      return stringProperty;
    }

    public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof ExtendedClass)) return false;
      final ExtendedClass other = (ExtendedClass) o;
      if (!other.canEqual((java.lang.Object) this)) return false;
      if (!super.equals(o)) return false;
      if (java.lang.Double.compare(this.getDoubleProperty(), other.getDoubleProperty()) != 0) return false;
      final java.lang.Object this$stringProperty = this.getStringProperty();
      final java.lang.Object other$stringProperty = other.getStringProperty();
      if (this$stringProperty == null ? other$stringProperty != null : !this$stringProperty.equals(other$stringProperty))
        return false;
      return true;
    }

    public int hashCode() {
      final int PRIME = 59;
      int result = 1;
      result = result * PRIME + super.hashCode();
      final long $doubleProperty = java.lang.Double.doubleToLongBits(this.getDoubleProperty());
      result = result * PRIME + (int) ($doubleProperty >>> 32 ^ $doubleProperty);
      final java.lang.Object $stringProperty = this.getStringProperty();
      result = result * PRIME + ($stringProperty == null ? 0 : $stringProperty.hashCode());
      return result;
    }

    protected boolean canEqual(Object other) {
      return other instanceof ExtendedClass;
    }
  }

  public static void main(String[] args) {
    final ExtendedClass test = new ExtendedClass();
    System.out.println(test.hashCode());
  }
}