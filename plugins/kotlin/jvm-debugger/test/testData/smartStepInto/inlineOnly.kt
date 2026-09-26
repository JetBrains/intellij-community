fun foo() {
    val a = mutableListOf("A", "B").also { it.add("C") }<caret>
    val b = a
}

// EXISTS: also: block.invoke()
// EXISTS: mutableListOf(T)
