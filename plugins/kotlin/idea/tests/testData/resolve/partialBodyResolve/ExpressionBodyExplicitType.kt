fun foo(): String = run {
    print("a")
    val v = 1
    print(<caret>v)
    "x"
}