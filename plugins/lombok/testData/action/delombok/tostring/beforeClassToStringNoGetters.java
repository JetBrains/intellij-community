@lombok.ToString(doNotUseGetters = true)
class ClassToStringNoGetters {
  private int intProperty;

  private float floatProperty;
  private float[] floatPropertyArray;

  private String stringProperty;
  private String[] stringPropertyArray;

  private static String staticStringProperty;
  private static String[] staticStringPropertyArray;

  int getIntProperty() {
    return intProperty;
  }

  float getFloatProperty() {
    return floatProperty;
  }

  float[] getFloatPropertyArray() {
    return floatPropertyArray;
  }

  String getStringProperty() {
    return stringProperty;
  }

  String[] getStringPropertyArray() {
    return stringPropertyArray;
  }

  static String getStaticStringProperty() {
    return staticStringProperty;
  }

  static String[] getStaticStringPropertyArray() {
    return staticStringPropertyArray;
  }
}
