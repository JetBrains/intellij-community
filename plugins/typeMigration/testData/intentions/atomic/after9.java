import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
  final AtomicInteger o;
  int j = o.get();

  Test(int o) {
    this.o = new AtomicInteger(o);
  }

  void foo() {
    while ((o = j) != 0) {}
  }
}