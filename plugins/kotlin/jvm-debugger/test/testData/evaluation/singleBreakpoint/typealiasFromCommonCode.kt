// MODULE: common
// FILE: common.kt

expect class ExpectTypeAlias()
val value = ExpectTypeAlias()

// MODULE: jvm
// FILE: typealiasFromCommonCode.kt

class JvmTypeAlias
actual typealias ExpectTypeAlias = JvmTypeAlias

fun main(){
    // EXPRESSION: value
    // RESULT: instance of JvmTypeAlias(id=ID): LJvmTypeAlias;
    //Breakpoint!
    val a = 0
}
