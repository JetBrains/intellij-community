import lombok.Value;

@Value
class Value3 {
  <caret>
  @lombok.experimental.NonFinal int x;
  int y;
}
