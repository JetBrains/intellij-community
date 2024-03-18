import test.TopLevelPropertyVisibilityBeforeKt;

class Test {
    static void test() {
        TopLevelPropertyVisibilityBeforeKt.getP();
        TopLevelPropertyVisibilityBeforeKt.setP(1);
    }
}