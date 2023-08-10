// "Change return type of called function 'A.component1' to 'Int'" "true"
abstract class A {
    abstract operator fun component1()
    abstract operator fun component2(): Int
}

fun foo(a: A) {
    val (w: Int, x: Int) = a<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix