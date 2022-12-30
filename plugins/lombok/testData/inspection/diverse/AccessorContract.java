
import lombok.experimental.Accessors;
import lombok.*;
import org.jetbrains.annotations.*;

@Accessors(fluent = true) @Value
class MyData {
  @Nullable String id;

  void test(MyData obj, Data2 data, int[] arr) {
    if (obj.id() != null && arr[0] == 1 && !obj.id().isEmpty()) {
      if (<warning descr="Condition 'obj.id() != null' is always 'true'">obj.id() != null</warning>) {}
      if (data.getField() == 1) return;
      if (<warning descr="Condition 'data.getField() == 1' is always 'false'">data.getField() == 1</warning>) return;
      data.withField2(12);
      if (<warning descr="Condition 'data.getField() == 1' is always 'false'">data.getField() == 1</warning>) return;
      if (<warning descr="Condition 'obj.id().length() > 0' is always 'true'">obj.id().length() > 0</warning>) {}
      data.setField(12);
      if (data.getField() == 1) return;
      if (obj.id() == null) return; // flushed unfortunately, due to "contains calls"
      if (<warning descr="Condition 'arr[0] == 1' is always 'true'">arr[0] == 1</warning>) {}
    }
  }
}
@AllArgsConstructor
class Data2 {
  @Getter @Setter
  int field;
  @Getter @With
  final int field2;
}