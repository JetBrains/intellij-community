class KotlinSub : JavaSuper() {
    override fun foo(a: Int, b: String) {
        val result = b + a
    }
}

fun test(sub: KotlinSub) {
    sub.foo(a = 10, b = "hello")
}
