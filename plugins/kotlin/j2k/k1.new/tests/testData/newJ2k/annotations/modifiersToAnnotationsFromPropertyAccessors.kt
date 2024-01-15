class WithModifiersOnAccessors {
    @Synchronized
    private fun methSync() {
    }

    @Strictfp
    protected fun methStrict() {
    }

    @get:Synchronized
    @set:Synchronized
    var sync: Int = 0

    @get:Strictfp
    var strict: Double = 0.0
}
