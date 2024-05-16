package test

expect interface BaseMethodOption {
    fun firstFun()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun firs/*rename*/tFun() {}
}