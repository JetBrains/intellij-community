package test.pkg

class Foo {
    fun method1(
        myInt: Int = 42,
        myInt2: Int? = null,
        myByte: Int = 2 * 21,
        str: String = "hello " + "world",
        vararg args: String
    ) { }

    fun method2(
        myInt: Int,
        myInt2: Int = (2*myInt) * SIZE
    ) { }

    fun method3(
        str: String,
        myInt: Int,
        myInt2: Int = double(myInt) + str.length
    ) { }

    fun emptyLambda(sizeOf: () -> Unit = {  }) {}

    companion object {
        fun double(myInt: Int) = 2 * myInt
        fun print(foo: Foo = Foo()) { println(foo) }

        private const val SIZE = 42
    }
}