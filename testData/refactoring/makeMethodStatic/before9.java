public class Foo {
    public int myData;
    int <caret>method(int i) {
        return myData + i;
    }
}

public class Bar extends Foo {
    int a(int b) {
        return method(b*2);
    }
}