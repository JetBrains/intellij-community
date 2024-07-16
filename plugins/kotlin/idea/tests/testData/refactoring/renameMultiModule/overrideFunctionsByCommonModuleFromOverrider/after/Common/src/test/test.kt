package test

expect interface BaseMethodOption {
    fun bar()
}

class BaseMethodOptionImpl : BaseMethodOption {
    override fun bar() {}
}