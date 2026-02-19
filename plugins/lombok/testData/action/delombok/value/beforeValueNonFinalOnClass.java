import lombok.Value;

@Value
@lombok.experimental.NonFinal
class Value2 {
  <caret>
  public int x;
  String name;
}
