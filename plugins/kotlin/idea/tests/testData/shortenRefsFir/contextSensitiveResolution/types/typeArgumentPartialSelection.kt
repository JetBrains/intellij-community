// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package usage

import test.Result

@Suppress("UNCHECKED_CAST")
fun handle(result: Result) {
    val success = result as test.Result.Success<<selection>test.Payload</selection>>
}
