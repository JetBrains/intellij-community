package target

class Foo {
    companion object : source.Klogging()

    fun baz() {
        logger.debug { "something" }
        log("something")
    }
}