class Test {
  @lombok.Getter
  private float b;
  @lombok.Getter(lombok.AccessLevel.PROTECTED)
  private double c;
  @lombok.Getter(lombok.AccessLevel.PRIVATE)
  private String d;

}
