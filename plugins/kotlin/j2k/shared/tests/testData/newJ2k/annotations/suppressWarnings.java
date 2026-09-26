@SuppressWarnings({"deprecation", "unused", "SpellCheckingInspection"})
public class A {

    @SuppressWarnings({"unused", "ALL"})
    public A() {
    }

    @SuppressWarnings({"ALL", "unused"})
    public int b = 0;

    @SuppressWarnings("unused")
    public void a(@SuppressWarnings("ALL") int i) {
    }

    @SuppressWarnings("HardCodedStringLiteral")
    public String s = "hello";
}