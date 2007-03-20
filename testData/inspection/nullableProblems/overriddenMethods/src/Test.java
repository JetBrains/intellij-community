import org.jetbrains.annotations.NotNull;

abstract class P2 {
    @NotNull
    String foo(@NotNull P p) {
        return "";
    }
}

class PPP extends P2 {
    String foo(P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    String foo(P p) {
        return super.foo(p);
    }
}

///////  in library
interface Foo {
    @NotNull
    String getTitle();
}
class FooImpl extends java.awt.Frame implements Foo {
//    public String getTitle() {
//        return super.getTitle();    //To change body of overridden methods use File | Settings | File Templates.
//    }
}
