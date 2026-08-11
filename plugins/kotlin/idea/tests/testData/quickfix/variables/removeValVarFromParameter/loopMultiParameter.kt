// "Remove 'val' from parameter" "true"
// K2_ERROR: VAL_OR_VAR_ON_LOOP_PARAMETER
class Pair<A, B>
{
    operator fun component1(): A = null!!
    operator fun component2(): B = null!!
}

fun f(list: List<Pair<String, String>>) {
    for (val<caret> (x,y) in list) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveValVarFromParameterFix