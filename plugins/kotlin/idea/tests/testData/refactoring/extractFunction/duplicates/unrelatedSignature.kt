// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: val x: kotlin.Int defined in test
// SUGGESTED_NAMES: i, getY

fun foo(p: Int): HashSet<String> {
    if (p > 0) {
        val b = p == 3
        fun localFun(s: String) = b

        val set = HashSet<String>()
        listOf("").filterTo(set, ::localFun)
    }

    return HashSet()
}

fun test() {
    val x = 1
    val y = <selection>x</selection>
}

// IGNORE_K1