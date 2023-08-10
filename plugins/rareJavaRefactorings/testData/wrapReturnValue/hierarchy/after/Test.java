abstract class Test {
    abstract Wrapper foo();

    public class Wrapper {
        private final String value;

        public Wrapper(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}