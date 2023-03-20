val g = lis<caret>tOf("a", "b", "c")

// INDEX_DEPENDENCIES_SOURCES: true
// DEPENDENCIES: classpath:runtime-classes; sources:runtime-source

// REF: (kotlin.collections).listOf(vararg T)
// FILE: collections/Collections.kt