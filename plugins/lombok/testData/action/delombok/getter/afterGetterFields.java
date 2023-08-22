class Test {
    private float b;
    private double c;
    private String d;
    private String e;
    private static String f;

    public static String getF() {
        return Test.f;
    }

    public float getB() {
        return this.b;
    }

    protected double getC() {
        return this.c;
    }

    private String getD() {
        return this.d;
    }
}
