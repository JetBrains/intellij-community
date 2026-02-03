fun main() {
    val x = 1
    val intToInt = hashMapOf<Int, Int>()
    val y = intToInt[x]
    // STEP_OVER: 1
    //Breakpoint!
    if (y != null) {
        println()
    }
    intToInt[x] = 0
}