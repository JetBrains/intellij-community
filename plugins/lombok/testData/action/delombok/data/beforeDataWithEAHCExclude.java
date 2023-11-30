import lombok.Data;
@lombok.Data
final class Class1 {
  <caret>

  private final String f1;

  @lombok.EqualsAndHashCode.Exclude
  private final String f2;
}