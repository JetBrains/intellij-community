class ClassName {
    private val labelProperty = "Outer property"

    fun printLabel<caret>Property() {
        println(this@ClassName.labelProperty)
    }

}

fun main() {
    ClassName().printLabelProperty()
}

// IGNORE_K1