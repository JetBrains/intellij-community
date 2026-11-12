// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// LANGUAGE_VERSION: 2.2
// CHECK_ERRORS_AFTER

class Session

fun Session.greet(): String = "hello"

fun Session.call(): String = this.greet()
