// "Change parameter 'f' type of function 'foo' to '() -> String'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
fun foo(f: () -> Int) {
    foo {
        ""<caret>
    }
}
// IGNORE_K2
// For K2, see KTIJ-33125