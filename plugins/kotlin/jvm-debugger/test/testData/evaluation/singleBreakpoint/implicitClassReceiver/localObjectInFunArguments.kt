open class InnerClass {
    open fun foo() = "foo"
}

open class Container {
    open fun add(obj: InnerClass) = obj.foo()
}

fun main() {
    val container = object : Container() {}

    container.add(object : InnerClass() {
        override fun foo(): String {
            // EXPRESSION: this
            // RESULT: instance of LocalObjectInFunArgumentsKt$main$1(id=ID): LLocalObjectInFunArgumentsKt$main$1;
            //Breakpoint!
            return super.foo() + "foo"
        }
    })
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES