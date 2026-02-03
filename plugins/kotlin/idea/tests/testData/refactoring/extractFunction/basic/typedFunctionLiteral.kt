class Bar

class Foo {
    fun bar(block: (Bar) -> Int) {}
    init {
        bar(<selection>{ bar -> 1 }</selection>)
    }
}

// IGNORE_K1