
import lombok.experimental.ExtensionMethod;
@ExtensionMethod({Ex.class})
class ConflictingOwnMethod {
  void m(Foo f) {
    f.or(f);
  }
}

class Foo {
  public void or(Foo foo) {}
}

class Ex {
  public static <T> T or(T t1, T t2) {
    return t1;
  }
}
