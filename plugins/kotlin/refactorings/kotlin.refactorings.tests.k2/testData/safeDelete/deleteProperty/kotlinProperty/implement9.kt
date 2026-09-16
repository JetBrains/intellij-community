interface A {
    val f<caret>oo: String
}

class X(override val foo: String,): A {}
