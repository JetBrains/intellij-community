class Test {
  @lombok.experimental.FieldNameConstants
  private float b;
  @lombok.experimental.FieldNameConstants(level=lombok.AccessLevel.PROTECTED)
  private double c;
  @lombok.experimental.FieldNameConstants(level=lombok.AccessLevel.PRIVATE)
  private String d;
  @lombok.experimental.FieldNameConstants(level=lombok.AccessLevel.NONE)
  private String e;
  @lombok.experimental.FieldNameConstants(level=lombok.AccessLevel.PACKAGE)
  private static String f;
}
