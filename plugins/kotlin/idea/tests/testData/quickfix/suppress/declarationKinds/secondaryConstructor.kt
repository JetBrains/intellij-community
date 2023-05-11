// "Suppress 'DIVISION_BY_ZERO' for secondary constructor of C" "true"

class C {
    constructor(s: Int = 2 / <caret>0)
}
