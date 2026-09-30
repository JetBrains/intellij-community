// "Remove type arguments" "true"
// K2_ERROR: UNRESOLVED_REFERENCE_WRONG_RECEIVER
// K2_AFTER_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS
class G<A> {
    companion object
}

fun G.Companion.foo() {}

fun test() {
    val reference: () -> Unit = G<<caret>*>::foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveCallableReferenceStaticLhsFixFactories$RemoveCallableReferenceStaticLhsFix
