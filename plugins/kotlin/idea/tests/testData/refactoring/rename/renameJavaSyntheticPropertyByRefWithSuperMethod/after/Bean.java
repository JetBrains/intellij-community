interface A {
    String getProp2();
    void setProp2(String prop);
}
public class Bean implements A {
    private String prop = "value";

    @Override
    public String getProp2() { return prop; }

    @Override
    public void setProp2(String prop) { this.prop = prop; }

    void test() {
        getProp2();
        setProp2("");
    }
}
