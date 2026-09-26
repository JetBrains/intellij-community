package kt25222

annotation class HelloWorld

class Foo {
    @HelloWorld
    fun bar() {}
}

fun main() {
    val clazz = Class.forName("kt25222.Foo")
    val bar = clazz.declaredMethods.first { it.name == "bar" }
    val ann = bar.annotations.first()

    //Breakpoint!
    val a = 5
}

// EXPRESSION: java.lang.reflect.Proxy.isProxyClass(ann::class.java)
// RESULT: 1: Z