// JVM_TARGET: 17
package test;

public record J(int x) {
    public int x() {
        return this.x < 100 ? this.x : 100;
    }
}