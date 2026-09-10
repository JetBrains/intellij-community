// NEW_NAME: bar
// RENAME: variable
data class Person(val name: String, val age: Int)

fun foo(p: Person) {
    (val n = name, val <caret>a = age) = p
    println("$n is $a years old")
}