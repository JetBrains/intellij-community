import inlibrary.test.*

val tl = <caret>topLevel()

// CONTEXT: return <ref-caret>local()
// WITH_LIBRARY: inLibrarySource

// REF: (in inlibrary.test.topLevel).local()