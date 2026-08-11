// "Replace array of boxed with array of primitive" "true"
// K2_ERROR: INVALID_TYPE_OF_ANNOTATION_MEMBER
annotation class SuperAnnotation(
        val c: <caret>Array<Char>,
        val str: Array<String>
)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TypeOfAnnotationMemberFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.TypeOfAnnotationMemberFix