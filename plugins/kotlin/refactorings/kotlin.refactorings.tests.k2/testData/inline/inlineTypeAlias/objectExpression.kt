typealias <caret>Kt = Kotlin

object Kotlin {
    fun member() = "Kotlin"
}

fun accept(value: Kotlin) = value.member()

fun test() {
    val language = Kt
    Kt.member()
    accept(Kt)
    when (language) {
        Kt -> println("Kotlin")
    }
    println(language)
}
