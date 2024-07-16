// ERROR: Cannot perform refactoring.\nInline recursive function is supported only on references

fun factor<caret>ial(n: Int): Int {
    return if (n == 0) 1 else n * factorial(n - 1)
}

fun main(args: Array<String>) {
    val result = factorial(5)
    println("Factorial of 5: $result")
}