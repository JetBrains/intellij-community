import lombok.Value;
@lombok.Value
class Class1 {
  <caret>

  String f1;

  @lombok.EqualsAndHashCode.Exclude
  String f2;
}