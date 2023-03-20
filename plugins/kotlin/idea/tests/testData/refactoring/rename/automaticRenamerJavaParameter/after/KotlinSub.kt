class KotlinSub : JavaSuper() {
    override fun foo(aa: Int, b: String) {
        val result = b + aa
    }
}

fun test(sub: KotlinSub) {
    sub.foo(aa = 10, b = "hello")
}
