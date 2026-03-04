fun main() {
    getEnum() == foo()<caret>
}

fun getEnum() = EnumClass.BRAVO
fun foo(): Any = EnumClass.BRAVO

enum class EnumClass {
    ALFA, BRAVO;
}

// EXISTS: getEnum(), equals(Any?), foo()
