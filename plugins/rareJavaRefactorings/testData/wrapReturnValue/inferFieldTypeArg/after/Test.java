import java.util.List;

class Test {
    Wrapper<String> foo() {
    return new Wrapper<String>(null);
  }

  void bar() {
    List<String> s = foo().getMyField();
  }

}