package defaultLambdaParameterInConstructor



// STEP_INTO: 1
// RESUME: 1
//Breakpoint!, lambdaOrdinal = 1
class A(f: () -> Unit = { foo() }) {
    init {
      f()
    }
}

fun main() {
    A()
}

fun foo() = Unit
