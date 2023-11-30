public class J {
    private String x;
    public String getX() {
        return x;
    }
    @Inapplicable
    public void setX(String s) {
        x = s;
    }

    private String y;
    public String getY() {
        return y;
    }
    @Applicable
    public void setY(String s) {
        y = s;
    }
}