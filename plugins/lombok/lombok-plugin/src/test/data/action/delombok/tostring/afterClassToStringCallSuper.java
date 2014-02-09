class ClassToStringCallSuper {
  private int intProperty;

  private float floatProperty;
  private float[] floatPropertyArray;

  private String stringProperty;
  private String[] stringPropertyArray;

  private static String staticStringProperty;
  private static String[] staticStringPropertyArray;

  public String toString() {
    return "ClassToStringCallSuper(super=" + super.toString() + ", intProperty=" + this.intProperty + ", floatProperty=" + this.floatProperty + ", floatPropertyArray=" + java.util.Arrays.toString(this.floatPropertyArray) + ", stringProperty=" + this.stringProperty + ", stringPropertyArray=" + java.util.Arrays.deepToString(this.stringPropertyArray) + ")";
  }
}
