public class OdinImpl extends Odin {
    public void justFun(@NotNull String s, int i) {}

    void test() {
        new OdinImpl().justFun("text", );
        new Odin().justFun("text");
    }
}