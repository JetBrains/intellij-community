// "Specify type explicitly" "true"
// K2_AFTER_ERROR: Unresolved reference 'KProperty0'.
private val foo: String = "hello"

fun test() {
    val p<caret> = ::foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.SpecifyTypeExplicitlyIntention