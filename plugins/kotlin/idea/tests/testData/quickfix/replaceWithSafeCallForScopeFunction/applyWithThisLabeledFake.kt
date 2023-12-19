// "Replace scope function with safe (?.) call" "false"
// WITH_STDLIB
// ACTION: Add non-null asserted (!!) call
// ACTION: Enable option 'Implicit receivers and parameters' for 'Lambdas' inlay hints
// ACTION: Introduce local variable
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type My?

class My(val prop: Int)

fun My?.foo(a: String?) {
    a.apply {
        this@foo<caret>.prop
    }
}