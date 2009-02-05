public class Builder {
    private int[] i;

    public void setI(int... i) {
        this.i = i;
    }

    public Test createTest() {
        return new Test(j, i);
    }
}