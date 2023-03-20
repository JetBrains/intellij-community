public class Foo {
    public boolean f1(int x, int y) {
        return x
               < y;
    }

    public boolean f2(int x, int y) {
        if (false) {
            return false;
        }
        return x
               < y;
    }
}