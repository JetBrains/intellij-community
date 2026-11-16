// ERROR: MUST_BE_INITIALIZED_OR_BE_ABSTRACT
class Test {
    private val s: String?
    var b: Boolean = false
    var d: Double = 0.0

    constructor() {
        b = true
    }

    constructor(s: String?) {
        this.s = s
    }
}
