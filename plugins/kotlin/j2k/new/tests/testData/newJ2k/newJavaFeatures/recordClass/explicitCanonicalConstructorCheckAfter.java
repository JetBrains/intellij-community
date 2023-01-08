// JVM_TARGET: 17
public record R(int x) {
    public R(int x) {
        this.x = x;
        if (x <= 0) throw new RuntimeException();
    }
}