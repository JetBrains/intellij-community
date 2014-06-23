public class Test<T extends Number> {
  T t;
  void foo() {
    int i = t.intValue();
  }
}
