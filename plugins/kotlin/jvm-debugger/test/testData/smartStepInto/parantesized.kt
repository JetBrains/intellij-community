fun foo() {
    (bar())<caret>
}

fun bar() {}

// EXISTS: bar()
// IGNORE_K2