final
class UtilityClass {
    private static long someField = System.currentTimeMillis();

    private UtilityClass() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void someMethod() {
        System.out.println();
    }

    protected static class InnerClass {
        private String innerInnerMember;
    }
}
