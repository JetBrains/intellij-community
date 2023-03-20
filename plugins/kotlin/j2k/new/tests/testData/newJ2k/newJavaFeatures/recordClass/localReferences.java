// JVM_TARGET: 17
public record J(int x) {
    void i(int y) {}

    void f1() {
        i(x + x() + this.x + this.x());
    }
    void f2(int x) {
        i(x + x() + this.x + this.x());
    }
    void f3(J j) {
        i(j.x + j.x());
    }
    void f4() {
        J j = new J(42);
        f3(new J(42));
        f3(j);
    }
}