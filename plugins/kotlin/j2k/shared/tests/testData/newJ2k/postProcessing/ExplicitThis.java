public class J {
    int i;
    void foo() {
        int x = this.i;
        x = this.i * -this.i;
        x = this.i > 0 ? this.i : this.i;
        this.i = x;
        System.out.println(this.i);
        this.foo();
    }
}