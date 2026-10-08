// ERROR: All inheritors must be named Kotlin classes outside the superclass.
open class <caret>Base {
    fun answer() = 42
}

val instance = object : Base() {}
