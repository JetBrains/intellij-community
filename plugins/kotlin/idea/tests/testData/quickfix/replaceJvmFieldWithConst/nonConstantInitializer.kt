// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: JvmField has no effect on a private property
// ACTION: Convert to lazy property
// ACTION: Make internal
// ACTION: Make public
// ACTION: Remove explicit type specification
// ACTION: Add use-site target 'field'
// ACTION: Remove @JvmField annotation
fun getText() = ""
<caret>@JvmField private val text: String = getText()