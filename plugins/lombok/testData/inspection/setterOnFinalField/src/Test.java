public class Test {
  @lombok.Setter
  private final int finalIntProperty = 0;

  @lombok.Setter(lombok.AccessLevel.PUBLIC)
  private final int finalPublicProperty = 0;

  @lombok.Setter(lombok.AccessLevel.PROTECTED)
  private final int finalProtectedProperty = 0;

  @lombok.Setter(lombok.AccessLevel.PACKAGE)
  private final int finalPackageProperty = 0;

  @lombok.Setter(lombok.AccessLevel.PRIVATE)
  private final int finalPrivateProperty = 0;

  @lombok.Setter(lombok.AccessLevel.NONE)
  private final int finalNoAccessProperty = 0;

  @lombok.Setter
  private int intProperty;

  @lombok.Setter(lombok.AccessLevel.PUBLIC)
  private int publicProperty;

  @lombok.Setter(lombok.AccessLevel.PROTECTED)
  private int protectedProperty;

  @lombok.Setter(lombok.AccessLevel.PACKAGE)
  private int packageProperty;

  @lombok.Setter(lombok.AccessLevel.PRIVATE)
  private int privateProperty;

  @lombok.Setter(lombok.AccessLevel.NONE)
  private int noAccessProperty;

}