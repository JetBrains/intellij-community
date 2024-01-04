// IGNORE_K2
fun b(body: () -> String) = body()

class A {
    fun test() = b {
        "st<selection>ri</selection>ng"
    }
}