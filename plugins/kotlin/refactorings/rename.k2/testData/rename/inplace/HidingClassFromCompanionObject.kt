// NEW_NAME: B
// RENAME: member
// SHOULD_FAIL_WITH: Class 'C' will be shadowed by class 'B'
private class A {

    class <caret>C {
        val p: B = B()
    }

    companion object {
        class B {}
    }
}