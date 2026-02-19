// NEW_NAME: m
// RENAME: member
enum class MyEnum {
    <caret>e;
    companion object { val m = 1 }
    fun context() = println(e.hashCode() + m)
}
fun external() = println(MyEnum.e.hashCode() + MyEnum.m)
// IGNORE_K1