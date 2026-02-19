// NEW_NAME: T
// RENAME: member
// SHOULD_FAIL_WITH: Type parameter 'T' is already declared in class 'O'
class O<T> {
    inner class B<<caret>K> {
        fun foo(t: T, k: K) {}
    }
}