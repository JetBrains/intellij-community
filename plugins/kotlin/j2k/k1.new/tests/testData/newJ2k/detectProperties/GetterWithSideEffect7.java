public class C {
    private String x = "old";

    public String getX() {
        System.out.println("side effect");
        return x;
    }

    public void setX(String x) {
        System.out.println("old value: " + this.x);
        this.x = x;
    }

    public static void main(String[] args) {
        C c = new C();
        c.setX("new");
    }
}