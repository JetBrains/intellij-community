// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: This annotation is not applicable to target 'top level property without backing field or delegate'
// ACTION: Make internal
// ACTION: Remove explicit type specification
// K2_AFTER_ERROR: WRONG_ANNOTATION_TARGET
// K2_ERROR: WRONG_ANNOTATION_TARGET
<caret>@JvmField val number: Int
    get() = 42