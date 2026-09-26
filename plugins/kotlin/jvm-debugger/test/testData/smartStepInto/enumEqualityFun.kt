fun main() {
    getEnum() == getEnum()<caret>
}

fun getEnum() = EnumClass.BRAVO

enum class EnumClass {
    ALFA, BRAVO;
}

// EXISTS: getEnum()_0, getEnum()_1
