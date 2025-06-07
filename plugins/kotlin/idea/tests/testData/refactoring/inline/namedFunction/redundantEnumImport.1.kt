package one

enum class EnumClass {
    A, B;
}

fun function() {
    function(EnumClass.A)
}

fun function(e: EnumClass) {}