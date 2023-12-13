package target

class Foo {
    companion object : Klogging()

    fun baz() {
        logger.debug { "something" }
        log("something")
    }
}