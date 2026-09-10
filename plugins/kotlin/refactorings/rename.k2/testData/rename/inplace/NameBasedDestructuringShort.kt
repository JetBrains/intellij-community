// NEW_NAME: bar
// RENAME: member
data class Person(val name: String, val a<caret>ge: Int)

fun foo(p: Person) {
    (val name, val age) = p
    println("$name is $age years old")
}