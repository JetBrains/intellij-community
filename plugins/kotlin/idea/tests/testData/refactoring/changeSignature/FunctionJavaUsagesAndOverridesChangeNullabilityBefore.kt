open class A {
    open fun <caret>foo(n: Int, s: String, o: Any?): String = ""
}
// IGNORE_K2 java change signature doesn't merge annotations if they are not type_use