// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
data class Person(val name: String, val age: Int)

fun foo(p: Person) {
    (val n = name, val <info descr="null">~a</info> = age) = p
    println("$n is $<info descr="null">a</info> years old")
}