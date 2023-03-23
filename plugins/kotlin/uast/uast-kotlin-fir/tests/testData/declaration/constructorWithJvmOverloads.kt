package test.pkg

class AllOptionalJvmOverloads @JvmOverloads constructor(
    private val foo: Int = 0,
    private val bar: Int = 0
)

class AllOptionalNoJvmOverloads(
    private val foo: Int = 0,
    private val bar: Int = 0
)

class SomeOptionalJvmOverloads @JvmOverloads constructor(
    private val foo: Int,
    private val bar: Int = 0
)

class SomeOptionalNoJvmOverloads(
    private val foo: Int,
    private val bar: Int = 0
)
