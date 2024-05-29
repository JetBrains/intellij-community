package smartStepIntoLibraryOverridenCall

fun main() {
    val range: ClosedRange<Int> = IntRange(1, 3)
    // SMART_STEP_INTO_BY_INDEX: 2
    //Breakpoint!
    System.out.println(range.contains(42))
}
