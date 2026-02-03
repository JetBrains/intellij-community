fun main() {
    5.<caret>f()
}

fun Int.f() = mutableMapOf<Int, Int>().apply {
    this@apply[this@f] = this@f
}

// IGNORE_K1