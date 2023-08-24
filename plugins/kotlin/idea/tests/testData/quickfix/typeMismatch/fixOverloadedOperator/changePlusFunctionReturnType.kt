// "Change return type of called function 'A.plus' to '() -> Int'" "true"
interface A {
    operator fun plus(a: A): String
}

fun foo(a: A): () -> Int {
    return a + a<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled