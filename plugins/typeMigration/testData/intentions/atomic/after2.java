import java.util.concurrent.atomic.AtomicReferenceArray;

// "Convert to atomic" "true"
class Test {
  AtomicReferenceArray<Object> field= new AtomicReferenceArray<>(foo());
  Object[] foo() {
    return null;
  }
}