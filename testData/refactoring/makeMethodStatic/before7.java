public class Foo {
            
    public int f(int i) {
        return 0;
    }

    public int <caret>method(int i) {
        return f(i);
    }
}