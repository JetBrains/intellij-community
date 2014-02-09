@lombok.ToString(exclude = {"floatProperty"})
class ClassToStringExclude {
  private int intProperty;

  private float floatProperty;
  private float[] floatPropertyArray;

  private String stringProperty;
  private String[] stringPropertyArray;

  private static String staticStringProperty;
  private static String[] staticStringPropertyArray;
}
