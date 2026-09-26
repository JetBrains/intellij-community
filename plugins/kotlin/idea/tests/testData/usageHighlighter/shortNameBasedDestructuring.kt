// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
data class Person(val name: String, val <info descr="null">age</info>: Int)

fun foo(p: Person) {
    (val name, val <info descr="null">~age</info>) = p
    println("$name is $<info descr="null">age</info> years old")
}