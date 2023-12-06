fun main() {
    class Inner {
        val bar: String
            get() {
                // EXPRESSION: this
                // RESULT: instance of LocalClass3Kt$main$Inner(id=ID): LLocalClass3Kt$main$Inner;
                //Breakpoint!
                return "bar"
            }
    }

    Inner().bar
}


// PRINT_FRAME