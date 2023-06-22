import inlibrary.test.*

val a: <caret>FunParameter? = null

// CONTEXT: val a = <ref-caret>p
// WITH_LIBRARY: inLibrarySource

// REF: p