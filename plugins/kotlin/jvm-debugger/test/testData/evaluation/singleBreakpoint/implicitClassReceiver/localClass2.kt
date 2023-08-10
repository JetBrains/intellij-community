// ATTACH_LIBRARY: simpleOpenClass

import forTests.*

fun main() {
    class Inner : SuperClass() {
        override fun bar(): Int {
            // EXPRESSION: this
            // RESULT: instance of LocalClass2Kt$main$Inner(id=ID): LLocalClass2Kt$main$Inner;
            //Breakpoint!
            return 2
        }
    }

    Inner().bar()
}


// PRINT_FRAME