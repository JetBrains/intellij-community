package target

public class A: B() {
    fun test() {
        bar()
        foo()
        this.bar()
        this.foo()
        B().foo().bar()
        library.B().foo().bar()
        B().bar().foo()
    }
}