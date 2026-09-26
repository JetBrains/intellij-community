fun foo() {
    val a = 1
    // SIBLING:
    if (<selection>a > 0</selection>) {
        fun bool(): Int { return 0 }
        println(bool())
    }
}