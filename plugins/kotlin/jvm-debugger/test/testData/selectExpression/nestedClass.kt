fun foo() {
    Foo.<caret>Bar::class.java
}

class Foo {
    class Bar
}

// EXPECTED: Foo.Bar::class
// EXPECTED_LEGACY: Foo.Bar