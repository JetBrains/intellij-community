// NEW_NAME: K
// RENAME: member
// SHOULD_FAIL_WITH: Type parameter 'K' is already declared in class 'B'
class O<<caret>T> {
    inner class B<K> {
        fun foo(t: T, k: K) {}
    }
}