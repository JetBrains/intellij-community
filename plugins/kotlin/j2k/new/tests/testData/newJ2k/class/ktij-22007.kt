internal object Foo {
    fun foo(ax: Double, ay: Double, cy: Double, denom: Double) {
        println((ay - ax - (cy * ax)) / denom)
    }
}
