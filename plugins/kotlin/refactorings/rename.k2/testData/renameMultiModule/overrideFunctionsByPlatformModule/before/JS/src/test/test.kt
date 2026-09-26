package test

actual interface BaseMethodOption {
    actual fun firstFun()
}

class BaseMethodOptionImplJvm : BaseMethodOption {
    override fun firs/*rename*/tFun() {}
}