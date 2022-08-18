import lombok.*;
import lombok.experimental.*;

@Value
@Builder
class Test {
  @NonFinal
  @Builder.Default
  int field = 123;

  @NonFinal
  int field2 = <warning descr="Variable 'field2' initializer '456' is redundant">456</warning>;
}
