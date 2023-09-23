
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

class Foo {
    private val content = listOf(1, 2, 3)
    suspend operator fun iterator(): Iterator<Int> = content.iterator()
}

fun main() {
    val x = Foo()

    //Breakpoint!
    println()
}

// EXPRESSION: buildList { for (i in x) { add(i) } }.sum()
// RESULT: 6: I
