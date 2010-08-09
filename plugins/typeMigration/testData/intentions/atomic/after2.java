import java.util.concurrent.atomic.AtomicReferenceArray;

// "Convert to atomic" "true"
class Test {
  AtomicReferenceArray<Object> field= new AtomicReferenceArray<Object>(foo());
  Object[] foo() {
    return null;
  }
}