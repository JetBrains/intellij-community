// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: This annotation is not applicable to target 'top level property without backing field or delegate'
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Remove explicit type specification
<caret>@JvmField val number: Int
    get() = 42