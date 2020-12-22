class ClassToStringOf {
  private int intProperty;

  private float floatProperty;
  private float[] floatPropertyArray;

  private String stringProperty;
  private String[] stringPropertyArray;

  private static String staticStringProperty;
  private static String[] staticStringPropertyArray;

  public String toString() {
    return "ClassToStringOf(floatProperty=" + this.floatProperty + ")";
  }
}
