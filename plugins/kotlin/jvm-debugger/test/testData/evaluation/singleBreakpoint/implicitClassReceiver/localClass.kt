fun main() {
    class Inner {
        fun foo(): String {
            // EXPRESSION: this
            // RESULT: instance of LocalClassKt$main$Inner(id=ID): LLocalClassKt$main$Inner;
            //Breakpoint!
            return "foo"
        }
    }

    Inner().foo()
}


// PRINT_FRAME