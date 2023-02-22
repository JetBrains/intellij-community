package inlinedLambdaInsideSam

fun main() {
    Runnable {
        with("abc") {
            //Breakpoint!
            this
        }
    }.run()
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
// IGNORE_FOR_K2_CODE
