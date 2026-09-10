// NEW_NAME: bar
// RENAME: member
data class Person(val name: String, val age: Int)

fun foo(p: Person) {
    (val name, val a<caret>ge) = p
    println("$name is $age years old")
}