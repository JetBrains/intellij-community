package bar

class Test {
    fun test(): Int = 5

    fun foo<caret>(): Int {
        return test()
    }
}