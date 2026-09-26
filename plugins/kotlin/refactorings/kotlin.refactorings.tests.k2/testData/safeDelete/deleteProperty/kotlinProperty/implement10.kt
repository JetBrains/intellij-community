interface A {
    val f<caret>oo: String
    val bar: Int
}

class X(override val foo: String, override val bar: Int,): A {}
