// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicReferenceArray;

class Test {
  AtomicReferenceArray<Object> field= new AtomicReferenceArray<Object>(foo());
  Object[] foo() {
    return null;
  }
}