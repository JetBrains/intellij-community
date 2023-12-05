// NEW_NAME: TypeP
// RENAME: member
package rename
class Ty<caret>peC
class ParamClass<TypeP> {
    fun useParam(p: TypeP) {
        if (p is TypeC) println()
    }
}

fun <TypeP> paramFun(p: TypeP) {
    if (p is TypeC) println()
}