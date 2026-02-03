// NEW_NAME: B
// RENAME: member
private class A {

    private class B {

        private class <caret>C {
            val b: B? = null
        }
    }
}