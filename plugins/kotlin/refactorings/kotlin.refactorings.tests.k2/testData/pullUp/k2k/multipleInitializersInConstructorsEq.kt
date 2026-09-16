// IGNORE_K2
// The test fails because `K2SemanticMatcher.isSemanticMatch(KaSession, KtElement, KtElement)` returns `false` for
// `n = a + 1` and `n = a plus 1`. This is probably acceptable, since the code is red (INFIX_MODIFIER_REQUIRED).
open class A

class <caret>B: A {
    // INFO: {"checked": "true"}
    var n: Int

    constructor(a: Int) {
        n = a + 1
        n = a plus 1
    }
}