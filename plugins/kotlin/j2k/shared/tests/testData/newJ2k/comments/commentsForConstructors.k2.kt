internal class A // this is a primary constructor
// this is a secondary constructor 1
// end of primary constructor body
@JvmOverloads constructor(p: Int = 1) {
    private val v = 1

    // end of secondary constructor 1 body

    // this is a secondary constructor 2
    constructor(s: String) : this(s.length) // end of secondary constructor 2 body
}

internal class B // this constructor will disappear
// end of constructor body
    (private var x: Int) {
    fun foo() {}
}

/*
     * The magic of comments
     */
// single line magic comments
internal class CtorComment {
    var myA: String? = "a"
}

internal class CtorComment2  /*
     * The magic of comments
     */
// single line magic comments
