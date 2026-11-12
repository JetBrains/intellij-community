// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2
// CHECK_ERRORS_AFTER

fun Session.greet(): String = "hello"

class Session {
    fun call(): String = this.greet()
}
