fun foo(p: () -> Int) {}
fun bar(b: Boolean) {
    foo {
        <selection>
            if (true) {
                return@foo 2
            }
        if (b) 3 else 5
        </selection>
    }
}
// IGNORE_K1