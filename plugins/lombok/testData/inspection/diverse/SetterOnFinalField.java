public class SetterOnFinalField {
  <warning descr="Not generating setter for this field: Setters cannot be generated for final fields.">@lombok.Setter</warning>
  private final int finalIntProperty = 0;

  <warning descr="Not generating setter for this field: Setters cannot be generated for final fields.">@lombok.Setter(lombok.AccessLevel.PUBLIC)</warning>
  private final int finalPublicProperty = 0;

  <warning descr="Not generating setter for this field: Setters cannot be generated for final fields.">@lombok.Setter(lombok.AccessLevel.PROTECTED)</warning>
  private final int finalProtectedProperty = 0;

  <warning descr="Not generating setter for this field: Setters cannot be generated for final fields.">@lombok.Setter(lombok.AccessLevel.PACKAGE)</warning>
  private final int finalPackageProperty = 0;

  <warning descr="Not generating setter for this field: Setters cannot be generated for final fields.">@lombok.Setter(lombok.AccessLevel.PRIVATE)</warning>
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
