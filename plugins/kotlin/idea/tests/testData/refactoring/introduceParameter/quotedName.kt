data class Person(val name: String, val pets: List<Pet>)
data class Pet(val name: String)

fun main() {
    val <caret>`⍼` = listOf(
        Person("Alice", listOf(Pet("Rex"), Pet("Fluffy"))),
        Person("Bob", listOf(Pet("Max")))
    )

    `⍼`.forEach {
        println("Person: ${it.name}")
        it.pets.forEach {
            println("Pet: ${it.name}")
        }
    }
}