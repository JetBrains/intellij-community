import java.util.Objects;

public class J {
    public static void main(String[] args) {
        Float posZero = 0.0f;
        Double negZero = -0.0;
        System.out.println(posZero.equals(negZero));
        System.out.println(Objects.equals(posZero, negZero));
        System.out.println(java.util.Objects.equals(posZero, negZero));
        System.out.println(posZero.equals(0));
        System.out.println(Objects.equals(0, negZero));
        System.out.println(Objects.equals(0, 0));
    }
}