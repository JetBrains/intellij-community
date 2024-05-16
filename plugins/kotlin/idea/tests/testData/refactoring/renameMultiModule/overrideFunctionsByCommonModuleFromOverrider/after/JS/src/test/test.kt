package test

actual interface BaseMethodOption {
    actual fun bar()
}

class BaseMethodOptionImplJvm : BaseMethodOption {
    override fun bar() {}
}