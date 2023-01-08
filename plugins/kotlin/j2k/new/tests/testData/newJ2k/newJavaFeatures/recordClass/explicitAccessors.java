// JVM_TARGET: 17
public record R(int x) {
    public int x() {
        return this.x < 100 ? this.x : 100;
    }
}