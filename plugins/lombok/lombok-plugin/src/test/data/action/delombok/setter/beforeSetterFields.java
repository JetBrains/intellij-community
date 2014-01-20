class Test {
  @lombok.Setter
  private float b;
  @lombok.Setter(lombok.AccessLevel.PROTECTED)
  private double c;
  @lombok.Setter(lombok.AccessLevel.PRIVATE)
  private String d;
  @lombok.Setter(lombok.AccessLevel.NONE)
  private String e;
  @lombok.Setter
  private static String f;
}
