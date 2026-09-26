// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package usage

fun handle(r: pkg.MyResult) {
    if (r is <selection>pkg.MyResult</selection>.Ok) {}
}
