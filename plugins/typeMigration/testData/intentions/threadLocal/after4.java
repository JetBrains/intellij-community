// "Convert to ThreadLocal" "true"
class Test {
    final ThreadLocal<String> field = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return "";
        }
    };
  void foo() {
    if (field.get().indexOf("a") == -1) return;
  }
}