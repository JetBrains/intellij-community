// FILE: main.kt
fun main(args: Array<String>) {
    //Breakpoint!
    val a = 1
}

inline fun sameFileFun() = object : Function<String> {}

// EXPRESSION: foo()
// RESULT: instance of AnotherKt$foo$1(id=ID): LAnotherKt$foo$1;

// EXPRESSION: sameFileFun()
// RESULT: instance of MainKt$sameFileFun$1(id=ID): LMainKt$sameFileFun$1;

// EXPRESSION: notInline()
// RESULT: instance of AnotherKt$notInline$1(id=ID): LAnotherKt$notInline$1;

// EXPRESSION: valObj
// RESULT: instance of AnotherKt$valObj$1(id=ID): LAnotherKt$valObj$1;

// EXPRESSION: notAnonObject()
// RESULT: instance of Obj(id=ID): LObj;

// FILE: another.kt
inline fun foo() = object : Function<String> {}

fun notInline() = object : Function<String> {}
val valObj = object : Function<String> {}
inline fun notAnonObject() = Obj
object Obj : Function<String>
