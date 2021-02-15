
import lombok.experimental.ExtensionMethod;
@ExtensionMethod({Ex.class})
class NestedExtensions {
  void m(String s) {
    s.or(s.or(s.or(s.or("finally"))));
  }
}

class NoExtensions {
  void m(String s) {
    s.<error descr="Cannot resolve method 'or' in 'String'">or</error>("finally");
  }
}

class Ex {
  public static <T> T or(T t1, T t2) {
    return t1;
  }
}
