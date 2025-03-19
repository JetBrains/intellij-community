// NEW_NAME: OT
// RENAME: member
// SHOULD_FAIL_WITH: Type parameter 'OT' is already declared in class 'P'

package rename

class P<OT> {
    inner class Bar<L> {
        fun <<caret>K> foo() {}
    }
}