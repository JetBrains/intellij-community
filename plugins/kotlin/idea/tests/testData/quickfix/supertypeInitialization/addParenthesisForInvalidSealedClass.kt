// "Change to constructor invocation" "false"
// ACTION: Introduce import alias
// ERROR: This type has a constructor, and thus must be initialized here
// ERROR: This type is sealed, so it can be inherited by only its own nested classes or objects
// K2_AFTER_ERROR: Local class cannot extend a sealed class.
// K2_AFTER_ERROR: This type has a constructor, so it must be initialized here.
sealed class A

fun test() {
    class B : A<caret>
}
