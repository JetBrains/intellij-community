fun main() {
    getEnum() == EnumClass.ALFA<caret>
}

fun getEnum() = EnumClass.BRAVO

enum class EnumClass {
    ALFA, BRAVO;
}

// EXISTS: getEnum()
