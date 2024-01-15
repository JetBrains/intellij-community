package test;

public class J implements I {
    private final int x;

    public J(int x) {
        this.x = x;
    }

    @Override
    public int getX() {
        return x;
    }
}
