public class J {
    public static void main(String[] args) {
        ieee(Double.NaN);
    }
    public static void ieee(double x) {
        System.out.println(!(x <= 0));
    }
}