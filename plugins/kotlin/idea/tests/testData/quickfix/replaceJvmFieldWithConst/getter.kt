// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: This annotation is not applicable to target 'top level property without backing field or delegate'
// K2_AFTER_ERROR: This annotation is not applicable to target 'top level property without backing field or delegate'. Applicable targets: field
// ACTION: Make internal
// ACTION: Remove explicit type specification
<caret>@JvmField val number: Int
    get() = 42