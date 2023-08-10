// ATTACH_LIBRARY: simpleOpenClass

import forTests.*

fun main() {
    val obj = object : SuperClass() {
        override fun bar(): Int {
            // EXPRESSION: this
            // RESULT: instance of LocalObject2Kt$main$obj$1(id=ID): LLocalObject2Kt$main$obj$1;
            //Breakpoint!
            return 2
        }
    }

    obj.bar()
}

// PRINT_FRAME