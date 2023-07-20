fun main() {
    val obj = object {
        fun foo(): String {
            // EXPRESSION: this
            // RESULT: instance of LocalObjectKt$main$obj$1(id=ID): LLocalObjectKt$main$obj$1;
            //Breakpoint!
            return "foo"
        }
    }

    obj.foo()
}

// PRINT_FRAME