// INPLACE_VARIABLE_NAME: s
fun b(body: () -> String) = body()

class A {
    fun test() = b {
        "st<selection>ri</selection>ng"
    }
}