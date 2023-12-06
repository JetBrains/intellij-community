@file:JvmName("Utils")
@file:JvmMultifileClass

package declaration

// Single-line comment bound to fun foo
fun foo(): Int = 42

/*
 * Multi-line comment bound to extension fun buzz
 */
fun String.buzz(): String {
    return "$this... zzz..."
}

/**
 * Multi-line document bound to property boo (w/ initializer)
 */
val boo = 42

/**
 * Multi-line document bound to property bar (w/ backing field access)
 */
val bar = 42
    // Single-line comment bound to getter for property bar
    get() = field

// Single-line comment bound to property baq (w/ backing field access)
val baq = 42
    get() = field

/**
 * Multi-line document bound to property baz (w/o backing field)
 */
val baz get() = 42
