internal annotation class Ann

internal annotation class Foo(@get:Ann val value: String, @get:Ann val value2: String = "test")
