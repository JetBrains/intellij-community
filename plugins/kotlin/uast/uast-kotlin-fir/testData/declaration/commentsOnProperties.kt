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

class Test {
    /**
     * Multi-line document bound to property foo (w/ initializer)
     */
    val foo = 42

    /**
     * Multi-line document bound to property far (w/ backing field access)
     */
    val far = 42
        // Single-line comment bound to getter for property bar
        get() = field

    // Single-line comment bound to property faq (w/ backing field access)
    val faq = 42
        get() = field

    /**
     * Multi-line document bound to property faz (w/o backing field)
     */
    val faz get() = 42
}
