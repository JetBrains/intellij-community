// JVM_TARGET: 17
public record R(int x) {
    public R(int x) {
        if (x <= 0) throw new RuntimeException();
        this.x = x;
    }
}