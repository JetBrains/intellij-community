class Test {
  @lombok.Getter
  private float b;
  @lombok.Getter(lombok.AccessLevel.PROTECTED)
  private double c;
  @lombok.Getter(lombok.AccessLevel.PRIVATE)
  private String d;
  @lombok.Getter(lombok.AccessLevel.NONE)
  private String e;
  @lombok.Getter
  private static String f;
}
