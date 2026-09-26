// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val name: String, val  <info descr="null">age</info>: Int)

fun foo(p: Person) {
    (val n = name, val a = <info descr="null">~age</info>) = p
    println("$n is $a years old")
}