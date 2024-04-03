package target

import source.logExt
import source.loggerExt

class Foo {
    companion object : source.Klogging()

    fun baz() {
        loggerExt.debug { "something" }
        logExt("something")
    }
}