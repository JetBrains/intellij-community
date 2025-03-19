public class OdinImpl extends Odin {
    @Override
    public void justFun<caret>(@NotNull String s) {}

    void test() {
        new OdinImpl().justFun("text");
        new Odin().justFun("text");
    }
}