public class Foo {
    public int myData;
    static int method(final Foo anObject, int i) {
        new Runnable () {
            void f() {};
            public void run() {
                this.f(anObject.myData);    
            }
        }
        return anObject.myData + anObject.myData;
    }
}