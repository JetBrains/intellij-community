// NEW_NAME: Foo
// RENAME: member

object `Foo-Bar` {
    class <caret>C
    fun m() {
        println(C().toString())
    }
}

class Foo
