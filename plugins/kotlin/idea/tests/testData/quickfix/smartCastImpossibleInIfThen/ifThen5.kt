// "Replace 'if' expression with safe access expression" "true"
// WITH_STDLIB
class Test {
    var x: Any? = null

    fun test() {
        if (x is String) foo(x<caret>)
    }

    fun foo(s: String) = 1
}