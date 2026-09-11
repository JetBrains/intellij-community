// "Surround with *arrayOf(...)" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ProhibitAssigningSingleElementsToVarargsInNamedForm -XXLanguage:-AllowAssigningArrayElementsToVarargsInNamedFormForFunctions
// DISABLE_ERRORS

fun anyFoo(vararg a: Any) {}

fun test() {
    anyFoo(a = in<caret>tArrayOf(1))
}
// IGNORE_K2

