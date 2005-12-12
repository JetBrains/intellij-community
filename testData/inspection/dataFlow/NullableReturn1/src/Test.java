import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  @NotNull Object foo() {
    Object res;
    res = null;
    return res;
  }
}