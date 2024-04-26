interface A {
    String getProp();
    void setProp(String prop);
}
public class Bean implements A {
    private String prop = "value";

    @Override
    public String getProp() { return prop; }

    @Override
    public void setProp(String prop) { this.prop = prop; }

    void test() {
        getProp();
        setProp("");
    }
}
