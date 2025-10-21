fun applyLam(block: () -> String) = block()

fun main() {
    applyLam {
        fun localFun() = "local fun"
        //Breakpoint!
        "OK"
    }
}

// EXPRESSION: localFun()
// RESULT: "local fun": Ljava/lang/String;

// IGNORE_K1
// SKIP_WRONG_DIRECTIVE_CHECK