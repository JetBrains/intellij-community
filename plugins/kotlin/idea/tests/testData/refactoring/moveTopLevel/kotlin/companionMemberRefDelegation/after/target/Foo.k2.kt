package target

class Foo {
    companion object : source.ILogging by source.Klogging()

    fun baz() {
        logger.debug { "something" }
        log("something")
    }
}