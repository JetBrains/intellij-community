// "Change return type of enclosing function 'test' to 'Any'" "true"
class O
class P

fun test(b: Boolean): O = if (b) O() else P()<caret>