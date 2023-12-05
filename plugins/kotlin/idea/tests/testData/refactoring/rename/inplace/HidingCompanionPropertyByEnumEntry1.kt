// NEW_NAME: e
// RENAME: member
enum class MyEnum {
    e;
    companion object { val <caret>m = 1 }
    fun context() = println(e.hashCode() + m)
}
fun external() = println(MyEnum.e.hashCode() + MyEnum.m)