// NEW_NAME: TypeC
// RENAME: member
package rename

class TypeC
class ParamClass<Ty<caret>peP> {
    fun useParam(p: TypeP) {
        if (p is TypeC) println()
    }
}

fun <TypeP> paramFun(p: TypeP) {
    if (p is TypeC) println()
}
// IGNORE_K1