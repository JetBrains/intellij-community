// "Specify type explicitly" "true"
private val foo: String = "hello"

fun test() {
    val p<caret> = ::foo
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention