import java.util.concurrent.atomic.AtomicIntegerArray;

// "Convert to atomic" "true"
class Test {
  AtomicIntegerArray field= new AtomicIntegerArray(foo());
  int[] foo() {
    return null;
  }
}