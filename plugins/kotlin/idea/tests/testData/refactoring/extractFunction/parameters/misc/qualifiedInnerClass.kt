class Foo {
    inner class Printer {
        fun print() {}
    }
}

class Caller {
    fun fooPrinter(foo: Foo) {
        <selection>val printer = foo.Printer()
        printer.print()</selection>
    }
}

// IGNORE_K1