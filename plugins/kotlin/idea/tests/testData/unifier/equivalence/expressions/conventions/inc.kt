// IGNORE_K2
operator fun String.inc() = this + "+"

class Foo {
    init {
        var s = ""
        <selection>s++</selection>
        s.inc()
        s = s.inc()
    }
}