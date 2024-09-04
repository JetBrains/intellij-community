//WITH_STDLIB

fun main() {
    var x by 42
    var y by ""
    var z by unresolved()
}

operator fun Any.getValue(thisRef: Any?, property: Any?): String = toString()