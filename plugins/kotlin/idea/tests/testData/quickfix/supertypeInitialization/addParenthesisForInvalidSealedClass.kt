// "Change to constructor invocation" "false"
// ACTION: Introduce import alias
// ERROR: This type has a constructor, and thus must be initialized here
// ERROR: This type is sealed, so it can be inherited by only its own nested classes or objects
// K2_AFTER_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS
// K2_AFTER_ERROR: SUPERTYPE_NOT_INITIALIZED
// K2_ERROR: SEALED_SUPERTYPE_IN_LOCAL_CLASS
// K2_ERROR: SUPERTYPE_NOT_INITIALIZED
sealed class A

fun test() {
    class B : A<caret>
}
