package test

interface BaseOne {
    fun newName()
}

interface BaseTwo {
    fun newName()
}

class Bar : BaseOne, BaseTwo {
    override fun newName() {}
}