// NEW_NAME: bar
// RENAME: variable
data class Person(val name: String, val age: Int)

fun foo(p: Person) {
    val (name, ag<caret>e) = p
    println("$name is $age years old")
}