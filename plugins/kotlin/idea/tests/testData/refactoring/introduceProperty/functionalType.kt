// EXTRACTION_TARGET: property with initializer

fun foo(p: (String) -> Boolean) {

}

fun bar() {
    foo <selection>{ it.isBlank() }</selection>
}

// IGNORE_K1