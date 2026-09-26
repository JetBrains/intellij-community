import java.lang.SuppressWarnings;

class C {
    @Deprecated private final int p1;
    private final int myP2;
    @SuppressWarnings("x") public int p3;
    @FieldOnly private final int p4;
    @FieldAndMethod private final int p5;
    @FieldAndParam private final int p6;

    public C(int p1, @Deprecated int p2, @Deprecated int p3, int p4, int p5, int p6) {
        this.p1 = p1;
        myP2 = p2;
        this.p3 = p3;
        this.p4 = p4;
        this.p5 = p5;
        this.p6 = p6;
    }
}
