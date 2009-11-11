// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicIntegerArray;

class Test {
  AtomicIntegerArray field= new AtomicIntegerArray(foo());
  int[] foo() {
    return null;
  }
}