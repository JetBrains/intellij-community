public class DoubleTrouble {
    public static void main(String[] args) {
        {
        Double a = Double.NaN;
        double b = a;//Double.NaN;
        if (b == Double.NaN) {
            ;
        }
        }

        if (Float.NaN != Float.NaN) {

        }

        {
          double a = Double.NaN;
          double b = Double.NaN;
          if (Double.NaN == a) {}
          if (b == a) {}
        }
    }
}
