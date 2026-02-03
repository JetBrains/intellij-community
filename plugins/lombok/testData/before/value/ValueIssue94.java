import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@RequiredArgsConstructor
public class Val {

  @NonFinal
  String nonFinal;

  String otherFinal;

  public void test() {
    Val val = new Val("otherFinal");
  }
}