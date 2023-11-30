public class J {
    public void literalThen() {
        double a = 3;
        double b = a != 0.0 ? 1 : a;
    }

    public void literalElse() {
        double a = 3;
        double b = a != 0.0 ? a : 1;
    }

    public void expressionThen() {
        double a = 3;
        double b = a != 0.0 ? 1 + 2 * 3 : a;
    }

    public void expressionElse() {
        double a = 3;
        double b = a != 0.0 ? a : 1 + 2 * 3;
    }


    public void sameType() {
        double a = 3;
        double b = a != 0.0 ? 1.0 : a;
    }
}