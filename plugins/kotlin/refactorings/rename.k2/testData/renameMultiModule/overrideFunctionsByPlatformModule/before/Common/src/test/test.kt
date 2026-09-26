package test

expect interface BaseMethodOption {
    fun firstFun()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun firstFun() {}
}