// "Suppress 'DIVISION_BY_ZERO' for companion object Companion of C" "true"

class C {
    companion object {
        var foo = 2 / <caret>0
    }
}
