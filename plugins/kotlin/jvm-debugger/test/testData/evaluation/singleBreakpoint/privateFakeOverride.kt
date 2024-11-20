open class Open {
    private val privateVal = 42
    private fun privateFun() = 43
    private val privateValCustomGetter get() = 44
    private var privateVar = 45
    private var privateVarCustomAccessors = 0
        get() {
            return field + 1
        }
        set(value) {
            field = value + 1
        }
}

class Child : Open()

fun main() {
    val child = Child()
    //Breakpoint!
    val x = 1
}

// EXPRESSION: child.privateVal
// RESULT: 42: I

// EXPRESSION: child.privateFun()
// RESULT: 43: I

// EXPRESSION: child.privateValCustomGetter
// RESULT: 44: I

// EXPRESSION: child.privateVar
// RESULT: 45: I

// EXPRESSION: child.privateVarCustomAccessors = 1; child.privateVarCustomAccessors
// RESULT: 3: I