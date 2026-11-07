// ERROR: 'getX' overrides nothing.
// ERROR: 'setX' overrides nothing.
// ERROR: 'x' hides member of supertype 'I' and needs an 'override' modifier.
internal interface I {
    var x: Int
}

internal class A(private var x: Int) : I {
    override fun getX(): Int {
        return x
    }

    override fun setX(x: Int) {
        this.x = x
    }
}
