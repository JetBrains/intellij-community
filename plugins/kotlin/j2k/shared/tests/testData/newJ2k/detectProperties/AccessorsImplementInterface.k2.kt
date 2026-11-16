// ERROR: NOTHING_TO_OVERRIDE
// ERROR: NOTHING_TO_OVERRIDE
// ERROR: VIRTUAL_MEMBER_HIDDEN
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
