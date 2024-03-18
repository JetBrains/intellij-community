// "Surround with null check" "false"
// ACTION: Add 'return@bar'
// ACTION: Add non-null asserted (!!) call
// ACTION: Enable option 'Implicit receivers and parameters' for 'Lambdas' inlay hints
// ACTION: Introduce local variable
// ACTION: Replace 'it' with explicit parameter
// ACTION: Replace with safe (?.) call
// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type Foo?
class Foo {
    fun foo(): Foo = Foo()
}

fun bar(f: (x: Foo?) -> Foo) {}

fun test() {
    bar {
        it<caret>.foo()
    }
}