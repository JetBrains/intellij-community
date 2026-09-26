public class OdinImpl extends Odin {
    public void justFun() {}

    void test() {
        new OdinImpl().justFun();
        new Odin().justFun("text");
    }
}