class Test {
    Wrapper foo() {
        return new Wrapper((o) -> {
            return 0;
        });
    }

    public class Wrapper {
        private final Comparable<String> value;

        public Wrapper(Comparable<String> value) {
            this.value = value;
        }

        public Comparable<String> getValue() {
            return value;
        }
    }
}