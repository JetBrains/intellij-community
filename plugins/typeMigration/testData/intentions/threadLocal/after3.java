// "Convert to ThreadLocal" "true"
class Test {
    ThreadLocal<Integer> field = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return new Integer(0);
        }
    };
  void foo() {
    if (field.get() == null) return;
  }
}