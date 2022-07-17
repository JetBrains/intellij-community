package anonymousObject

fun main() {
    val localObject = object { }
    //Breakpoint!
    val a = 1
}

// EXPRESSION: localObject
// RESULT: instance of anonymousObject.AnonymousObjectKt$main$localObject$1(id=ID): LanonymousObject/AnonymousObjectKt$main$localObject$1;

