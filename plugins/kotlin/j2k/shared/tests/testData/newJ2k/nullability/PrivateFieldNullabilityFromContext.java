public class Foo {
    private Integer i = new J().getInt(); // initialized as a platform type
    private Integer j;
    private Integer k = new J().getInt();
    private Integer l;

    public Foo() {
        this.j = new J().getInt(); // initialized as a platform type
        this.l = new J().getInt();
    }

    void test() {
        System.out.println(i + i); // used as a not-null type
        System.out.println(j + j); // used as a not-null type

        if (k != null) {
            System.out.println(k + k);
        }

        if (l != null) {
            System.out.println(l + l);
        }
    }
}