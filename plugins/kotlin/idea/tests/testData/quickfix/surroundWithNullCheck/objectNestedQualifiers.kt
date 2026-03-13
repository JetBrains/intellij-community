// "Surround with null check" "true"
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.

fun foo(p: String?) {
    Util.f1(Util.f2(p<caret>.length), 0)
}

object Util {
    fun f1(o: Any, p: Int): Any = o
    fun f2(o: Any): Any = o
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix