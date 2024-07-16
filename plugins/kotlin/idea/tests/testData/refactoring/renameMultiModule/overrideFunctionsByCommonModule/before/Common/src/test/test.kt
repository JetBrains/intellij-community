package test

expect interface BaseMethodOption {
    fun first/*rename*/Fun()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun firstFun() {}
}