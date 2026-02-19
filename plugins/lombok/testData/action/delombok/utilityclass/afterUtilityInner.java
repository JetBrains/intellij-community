class UtilityInner {
    static class InnerInner {
        static final
        class InnerInnerInner {
            static int member;

            private InnerInnerInner() {
                throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
            }
        }
    }

    enum UtilityInsideEnum {
        FOO, BAR;

        static final
        class InsideEnum {
            static int member;

            private InsideEnum() {
                throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
            }
        }
    }

    interface UtilityInsideInterface {
        final
        class InsideInterface {
            static int member;

            private InsideInterface() {
                throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
            }
        }
    }
}
