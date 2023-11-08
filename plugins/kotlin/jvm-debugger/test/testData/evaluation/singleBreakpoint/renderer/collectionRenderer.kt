package collectionRenderer

private class MyList : AbstractList<String>() {
    private val implementationDetail = "should not be seen"

    override val size: Int = 2

    override fun get(index: Int): String = when (index) {
        0 -> "first"
        1 -> "second"
        else -> throw IndexOutOfBoundsException(index)
    }
}

fun main() {
    val emptyList = emptyList<String>()
    val nonEmptyList = listOf("l1", "l2", "l3")

    val emptySet = emptySet<String>()
    val nonEmptySet = setOf("s1", "s2", "s3")

    val builtEmptyList = buildList<String> {}
    val builtNonEmptyList = buildList { add("bl1"); add("bl2"); add("bl3") }

    val builtEmptySet = buildSet<String> {}
    val builtNonEmptySet = buildSet {
        add("bs1")
        add("bs2")
        add("bs3")
    }

    val myList = MyList()

    //Breakpoint!
    run {}
}

// PRINT_FRAME
