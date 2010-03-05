// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicInteger;

class Test {
  AtomicInteger o = new AtomicInteger(0);
  int j = o.get();

  void foo() {
    while ((o = j) != 0) {}
  }
}