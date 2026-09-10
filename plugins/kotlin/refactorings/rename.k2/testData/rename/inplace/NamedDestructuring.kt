// NEW_NAME: s
// RENAME: member
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
data class ReportedData(val p<caret>1: String, val p2: Int)

@Suppress("UnusedVariable", "unused")
fun reportedContext(rd: ReportedData, flag: Int) {
    when (flag) {
        1 -> {
            (val p1, val p2) = rd
        }
        2 -> {
            (val p1, val p2) = rd
        }
        else -> {}
    }
}