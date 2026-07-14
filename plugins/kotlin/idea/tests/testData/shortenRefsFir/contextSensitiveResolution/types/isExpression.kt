// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

sealed class MyResult {
    class Ok(val value: String) : MyResult()
    class Err(val message: String) : MyResult()
}

fun handle(r: MyResult) {
    if (r is <selection>MyResult.Ok</selection>) {}
}
