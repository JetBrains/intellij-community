fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    getEnum() == getEnum()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    getEnum() == getEnum()
}

fun getEnum(): EnumClass {
    val value = EnumClass.BRAVO
    return value
}

enum class EnumClass {
    ALFA, BRAVO;
}
