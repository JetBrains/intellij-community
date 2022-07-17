// "Change return type of enclosing function 'test2' to 'List<Any>'" "true"
// WITH_STDLIB

fun test2(ss: List<Any>) {
    return ss.map { it }<caret>
}