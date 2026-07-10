// "Remove branch" "true"
enum class BranchType {
    TYPE1, TYPE2, TYPE3;

    fun test(type: BranchType) {

        when(type) {
            TYPE1 -> TODO()
            TYPE2 -> TODO()
            TYPE2<caret> -> TODO()
            TYPE3 -> TODO()
        }
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveWhenBranchFix