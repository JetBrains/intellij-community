public class RenameCollisions {
    public static final int STATIC_FIELD = 5;
    private class InnerClass {
        public static final int INNER_STATIC_FIELD<caret> = 13;
        public void instanceContext(int param3) {
            int localVar3 = 0;
            int var1 = localVar3 + param3 + INNER_STATIC_FIELD + STATIC_FIELD;
        }
    }
}
