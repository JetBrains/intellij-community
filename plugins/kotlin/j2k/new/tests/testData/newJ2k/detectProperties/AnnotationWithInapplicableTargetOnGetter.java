public class J {
    private String x;
    @Inapplicable
    public String getX() {
        return x;
    }
    public void setX(String s) {
        x = s;
    }

    private String y;
    @Applicable
    public String getY() {
        return y;
    }
    public void setY(String s) {
        y = s;
    }
}