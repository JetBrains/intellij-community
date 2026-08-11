package test

enum class MyEnum { A, B }

fun expectsMyEnum(e: MyEnum) {}

fun test() {
    expectsMyEnum(<selection>MyEnum.A</selection>)
}
