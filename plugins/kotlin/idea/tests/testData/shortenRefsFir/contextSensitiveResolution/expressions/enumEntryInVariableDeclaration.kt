// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

enum class MyEnum { A, B }

fun test() {
    val e: MyEnum = <selection>MyEnum.A</selection>
}
