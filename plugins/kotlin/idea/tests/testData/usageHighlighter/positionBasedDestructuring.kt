// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val name: String, val age: Int)

fun foo(p: Person) {
    [val name, val <info descr="null">~a</info>] = p
    println("$name is $<info descr="null">a</info> years old")
}