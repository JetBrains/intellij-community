import kotlin.reflect.KProperty

var increment = 0
val x: Int
    get() {
        if (increment % 2 == 0) {
            val z = 1
        } else {
            val z = 2
        }
        return increment++
    }

fun testProperty() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    x + x

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    x + x
}

fun main() {
    testProperty()
}
