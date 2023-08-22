// "Replace '@JvmField' with 'const'" "false"
// WITH_STDLIB
// ERROR: JvmField has no effect on a private property
// ACTION: Convert to lazy property
// ACTION: Make internal
// ACTION: Make public
// ACTION: Specify type explicitly
// ACTION: Add use-site target 'field'
// ACTION: Remove @JvmField annotation
val three = 3
<caret>@JvmField private val text = "${2 + three}"