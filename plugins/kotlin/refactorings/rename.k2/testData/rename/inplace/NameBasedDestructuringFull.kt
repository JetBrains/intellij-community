// NEW_NAME: bar
// RENAME: member
data class Person(val name: String, val a<caret>ge: Int)

fun foo(p: Person) {
    (val n = name, val a = age) = p
    println("$n is $a years old")
}