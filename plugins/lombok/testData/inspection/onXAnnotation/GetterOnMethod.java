
public class GetterOnMethod {
  @lombok.Getter(onMethod = @__(@Deprecated))
  private int intField;

  @lombok.Getter(onMethod_ = {@Deprecated})
  private int intField2;
}
