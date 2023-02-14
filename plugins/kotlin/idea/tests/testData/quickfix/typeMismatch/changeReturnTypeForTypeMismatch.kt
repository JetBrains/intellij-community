// "Change return type of enclosing function 'test1' to 'Int'" "true"
// WITH_STDLIB

fun test1(ss: List<Any>) {
    return ss.map { it }.size<caret>
}