package valueParameterOfLocalType

fun main() {
    class Local

    fun local(x: Local) {
        //Breakpoint!
        x
    }

    local(Local())
}

// EXPRESSION: x
// RESULT: instance of valueParameterOfLocalType.ValueParameterOfLocalTypeKt$main$Local(id=ID): LvalueParameterOfLocalType/ValueParameterOfLocalTypeKt$main$Local;
