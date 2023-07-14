// "Replace array of boxed with array of primitive" "true"
annotation class SuperAnnotation(
        val i: <caret>Array<Int>,
        val str: Array<String>
)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TypeOfAnnotationMemberFix