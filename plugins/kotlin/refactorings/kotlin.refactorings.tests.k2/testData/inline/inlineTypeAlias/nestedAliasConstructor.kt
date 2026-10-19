typealias BaseList<T> = java.util.ArrayList<T>
typealias <caret>Alias<T> = BaseList<T>

fun main() {
    val list: Alias<String> = Alias()
    println(list)
}
// IGNORE_K2