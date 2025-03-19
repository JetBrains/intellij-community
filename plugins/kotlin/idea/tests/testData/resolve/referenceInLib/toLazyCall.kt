import inlibrary.test.*

fun m() {
    val a = delegat<caret>eMe
}

// CONTEXT: val delegateMe by la<ref-caret>zy { 1 }
// WITH_LIBRARY: inLibrarySource

// REF: (kotlin).lazy(() -> T)