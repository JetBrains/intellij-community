package test

interface BaseOne {
    fun oldName()
}

interface BaseTwo {
    fun oldName()
}

class Bar : BaseOne, BaseTwo {
    override fun oldName() {}
}