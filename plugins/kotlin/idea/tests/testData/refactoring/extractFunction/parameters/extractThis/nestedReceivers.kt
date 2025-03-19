fun bar() {
    with("") { A22().apply { <selection>b(plus(""))</selection> } }
}

class A22 {
    fun b(a: String) {}
}

// IGNORE_K1