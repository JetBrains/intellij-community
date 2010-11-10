import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
  final AtomicInteger o = new AtomicInteger(0);

  void foo() {
    boolean b = this.o.get() == 1;
  }
}