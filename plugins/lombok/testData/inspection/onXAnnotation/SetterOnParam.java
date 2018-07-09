
public class SetterOnParam {
  @lombok.Setter(onParam = @__(@Deprecated))
  private int intField;

  @lombok.Setter(onParam_ = {@Deprecated})
  private int intField2;
}
