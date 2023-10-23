package idea335188

fun main() {
    fun localF() {
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = 1
        run { foo() }
    }
    localF()
}

fun foo() = Unit
