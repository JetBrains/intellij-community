// "Convert to ThreadLocal" "true"
class X {
    final ThreadLocal<Integer> i = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };
}