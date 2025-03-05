// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: JvmField has no effect on a private property
// K2_AFTER_ERROR: JvmField has no effect on a private property.
// ACTION: Add use-site target 'field'
// ACTION: Convert to lazy property
// ACTION: Make internal
// ACTION: Make public
// ACTION: Remove @JvmField annotation
// ACTION: Remove explicit type specification
<caret>@JvmField private val number: Int? = 42