public class Foo {
    public int myData;
    static int <caret>method(Foo anObject, int i) {
        return anObject.myData + i;
    }
}

public class Bar extends Foo {
    int method(int b) {
        return super.method(this, b*2);
    }
}